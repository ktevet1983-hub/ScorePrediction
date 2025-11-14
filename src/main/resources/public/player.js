"use strict";

const $ = (sel) => document.querySelector(sel);
const titleEl = $("#playerTitle");
const contentEl = $("#playerContent");
const headerCardEl = $("#playerHeader");
const headerRowEl = document.querySelector("#playerHeader .player-header");
const metaEl = $("#playerMeta");
const leaguesEl = $("#playerLeagues");

function nowMs() { try { return performance.now(); } catch { return Date.now(); } }
function log(...args) { try { console.log("[player.js]", ...args); } catch {} }

// Abort control per navigation
let currentAbortController = null;
function startAbortController() {
	if (currentAbortController) {
		try { currentAbortController.abort(); } catch {}
	}
	currentAbortController = new AbortController();
	try {
		window.addEventListener("beforeunload", () => {
			try { currentAbortController.abort(); } catch {}
		}, { once: true });
	} catch {}
	return currentAbortController.signal;
}

// Caches
const PROFILE_VERSION = "v1";
const STATS_VERSION = "v1";
const inFlight = new Map(); // key -> Promise
const profileCache = new Map(); // playerId -> object
const statsCache = new Map(); // playerId:season -> object

function getQuery() {
	const out = {};
	const s = window.location.search.replace(/^\?/, "");
	for (const part of s.split("&")) {
		if (!part) continue;
		const [k, v = ""] = part.split("=");
		out[decodeURIComponent(k)] = decodeURIComponent(v);
	}
	return out;
}

function sessionKeyProfile(playerId) {
	return `playerProfile:${playerId}:${PROFILE_VERSION}`;
}
function sessionKeyStats(playerId, season) {
	return `playerStats:${playerId}:${season}:${STATS_VERSION}`;
}

function valOrDash(v) {
	if (v === 0) return "0";
	if (v === false) return "0";
	return (v == null || String(v).trim() === "") ? "-" : String(v);
}

async function getOrFetchProfile(playerId, signal) {
	const k = String(playerId);
	if (profileCache.has(k)) return profileCache.get(k);
	try {
		const ss = sessionStorage.getItem(sessionKeyProfile(k));
		if (ss) {
			const obj = JSON.parse(ss);
			profileCache.set(k, obj);
			return obj;
		}
	} catch {}
	const inflightKey = `profile:${k}`;
	if (inFlight.has(inflightKey)) return inFlight.get(inflightKey);
	const p = (async () => {
		const resp = await fetch(`/playerProfile?player=${encodeURIComponent(k)}`, { cache: "no-store", signal });
		const text = await resp.text();
		let json;
		try {
			json = JSON.parse(text);
		} catch {
			throw new Error("Invalid profile response");
		}
		if (!resp.ok) {
			throw new Error(json?.error || "Failed to load profile");
		}
		try { sessionStorage.setItem(sessionKeyProfile(k), JSON.stringify(json)); } catch {}
		profileCache.set(k, json);
		return json;
	})();
	inFlight.set(inflightKey, p);
	try {
		const out = await p;
		return out;
	} finally {
		inFlight.delete(inflightKey);
	}
}

async function getOrFetchStats(playerId, season, signal) {
	const k = `${playerId}:${season}`;
	if (statsCache.has(k)) return statsCache.get(k);
	try {
		const ss = sessionStorage.getItem(sessionKeyStats(playerId, season));
		if (ss) {
			const obj = JSON.parse(ss);
			statsCache.set(k, obj);
			return obj;
		}
	} catch {}
	const inflightKey = `stats:${k}`;
	if (inFlight.has(inflightKey)) return inFlight.get(inflightKey);
	const p = (async () => {
		const url = `/playerStats?player=${encodeURIComponent(String(playerId))}&season=${encodeURIComponent(String(season))}`;
        const resp = await fetch(url, { cache: "no-store", signal });
		const text = await resp.text();
		let json;
		try {
			json = JSON.parse(text);
		} catch {
			throw new Error("Invalid stats response");
		}
		if (!resp.ok) {
			throw new Error(json?.error || "Failed to load stats");
		}
		try { sessionStorage.setItem(sessionKeyStats(playerId, season), JSON.stringify(json)); } catch {}
		statsCache.set(k, json);
		return json;
	})();
	inFlight.set(inflightKey, p);
	try {
		const out = await p;
		return out;
	} finally {
		inFlight.delete(inflightKey);
	}
}

function extractProfileFields(profileJson, statsJson) {
	const r = (profileJson && profileJson.response && profileJson.response[0]) || {};
	const p = r.player || r || {};
	// stats fallback for position/team
	const sEntry = Array.isArray(statsJson?.response) ? statsJson.response[0] : null;
	let firstStat = null;
	if (sEntry && Array.isArray(sEntry.statistics) && sEntry.statistics.length) {
		firstStat = sEntry.statistics[0];
	} else if (Array.isArray(statsJson?.response)) {
		// flatten search
		for (const it of statsJson.response) {
			if (it && Array.isArray(it.statistics) && it.statistics.length) {
				firstStat = it.statistics[0];
				break;
			}
		}
	}

	return {
		name: p.name || "-",
		age: p.age ?? "-",
		nationality: p.nationality || (p.birth && p.birth.country) || "-",
		height: p.height || "-",
		weight: p.weight || "-",
		photo: p.photo || "",
		position: (firstStat && firstStat.games && firstStat.games.position) || "-",
		teamName: (firstStat && firstStat.team && firstStat.team.name) || "-",
		teamLogo: (firstStat && firstStat.team && firstStat.team.logo) || ""
	};
}

// Header rendering now handled by player.html structure (sp-card + sp-kv)

function normalizeStatsByLeague(statsJson) {
	const out = new Map(); // leagueId -> { league, team, stats[] }
	const items = Array.isArray(statsJson?.response) ? statsJson.response : [];
	for (const entry of items) {
		const arr = Array.isArray(entry?.statistics) ? entry.statistics : [];
		for (const st of arr) {
			const leagueId = (st.league && st.league.id) != null ? String(st.league.id) : (st.league && st.league.name) || "unknown";
			if (!out.has(leagueId)) {
				out.set(leagueId, {
					league: st.league || {},
					stats: []
				});
			}
			out.get(leagueId).stats.push(st);
		}
	}
	return out;
}

function renderAccordionForLeague(leagueKey, bucket) {
	const leagueName = bucket.league?.name || "League";
	const country = bucket.league?.country ? ` — ${bucket.league.country}` : "";

	const acc = document.createElement("div");
	acc.className = "group sp-card accordion";

	const header = document.createElement("div");
	header.className = "group-header sp-card__header";
	header.style.cursor = "pointer";
	header.innerHTML = `<span class="chev">▶</span>${leagueName}${country}`;
	header.addEventListener("click", () => {
		const open = acc.classList.toggle("open");
	});

	const body = document.createElement("div");
	body.className = "accordion-body";

	if (!Array.isArray(bucket.stats) || bucket.stats.length === 0) {
		body.innerHTML = `<div style="color:var(--sp-text-muted);padding:12px">No stats available</div>`;
	} else {
		// Aggregate per league (some APIs return multiple teams in the league; merge conservatively)
		const agg = {
			appearances: 0, minutes: 0, lineups: 0, position: undefined, rating: undefined,
			goals: 0, assists: 0, conceded: 0, saves: 0,
			shotsTotal: 0, shotsOn: 0,
			passesTotal: 0, passesKey: 0, passesAccuracy: 0,
			duelsTotal: 0, duelsWon: 0,
			dribblesAttempts: 0, dribblesSuccess: 0,
			foulsDrawn: 0, foulsCommitted: 0,
			yellow: 0, red: 0,
			penScored: 0, penMissed: 0, penSaved: 0
		};
		for (const st of bucket.stats) {
			const g = st.games || {};
			const goals = st.goals || {};
			const shots = st.shots || {};
			const passes = st.passes || {};
			const duels = st.duels || {};
			const drb = st.dribbles || {};
			const fouls = st.fouls || {};
			const cards = st.cards || {};
			const pen = st.penalty || {};

			agg.appearances += Number(g.appearences ?? g.appearances ?? 0); // some APIs misspell
			agg.minutes += Number(g.minutes ?? 0);
			agg.lineups += Number(g.lineups ?? 0);
			agg.position = agg.position || g.position;
			agg.rating = agg.rating || (g.rating ? String(g.rating) : undefined);

			agg.goals += Number(goals.total ?? 0);
			agg.assists += Number(goals.assists ?? 0);
			agg.conceded += Number(goals.conceded ?? 0);
			agg.saves += Number(goals.saves ?? 0);

			agg.shotsTotal += Number(shots.total ?? 0);
			agg.shotsOn += Number(shots.on ?? 0);

			agg.passesTotal += Number(passes.total ?? 0);
			agg.passesKey += Number(passes.key ?? 0);
			// 'accuracy' may be "%", keep raw
			agg.passesAccuracy = agg.passesAccuracy || passes.accuracy;

			agg.duelsTotal += Number(duels.total ?? 0);
			agg.duelsWon += Number(duels.won ?? 0);

			agg.dribblesAttempts += Number(drb.attempts ?? 0);
			agg.dribblesSuccess += Number(drb.success ?? 0);

			agg.foulsDrawn += Number(fouls.drawn ?? 0);
			agg.foulsCommitted += Number(fouls.committed ?? 0);

			agg.yellow += Number(cards.yellow ?? 0);
			agg.red += Number(cards.red ?? 0);

			agg.penScored += Number(pen.scored ?? 0);
			agg.penMissed += Number(pen.missed ?? 0);
			agg.penSaved += Number(pen.saved ?? 0);
		}

		const kv = document.createElement("div");
		kv.className = "sp-kv";
		kv.innerHTML = `
			<div class="sp-kv__label">Appearances</div><div class="sp-kv__value">${agg.appearances}</div>
			<div class="sp-kv__label">Minutes</div><div class="sp-kv__value">${agg.minutes}</div>
			<div class="sp-kv__label">Lineups</div><div class="sp-kv__value">${agg.lineups}</div>
			<div class="sp-kv__label">Position</div><div class="sp-kv__value">${valOrDash(agg.position)}</div>
			<div class="sp-kv__label">Rating</div><div class="sp-kv__value">${valOrDash(agg.rating)}</div>
			<div class="sp-kv__label">Goals</div><div class="sp-kv__value">${agg.goals}</div>
			<div class="sp-kv__label">Assists</div><div class="sp-kv__value">${agg.assists}</div>
			<div class="sp-kv__label">Shots (total/on)</div><div class="sp-kv__value">${agg.shotsTotal}/${agg.shotsOn}</div>
			<div class="sp-kv__label">Passes (total/key)</div><div class="sp-kv__value">${agg.passesTotal}/${agg.passesKey}</div>
			<div class="sp-kv__label">Pass accuracy</div><div class="sp-kv__value">${valOrDash(agg.passesAccuracy)}</div>
			<div class="sp-kv__label">Duels (total/won)</div><div class="sp-kv__value">${agg.duelsTotal}/${agg.duelsWon}</div>
			<div class="sp-kv__label">Dribbles (att/success)</div><div class="sp-kv__value">${agg.dribblesAttempts}/${agg.dribblesSuccess}</div>
			<div class="sp-kv__label">Fouls (drawn/committed)</div><div class="sp-kv__value">${agg.foulsDrawn}/${agg.foulsCommitted}</div>
			<div class="sp-kv__label">Cards (Y/R)</div><div class="sp-kv__value">${agg.yellow}/${agg.red}</div>
			<div class="sp-kv__label">Penalties (scored/missed/saved)</div><div class="sp-kv__value">${agg.penScored}/${agg.penMissed}/${agg.penSaved}</div>
		`;
		body.appendChild(kv);
	}

	acc.appendChild(header);
	acc.appendChild(body);
	return acc;
}

function renderLoading() {
	if (leaguesEl) {
		leaguesEl.innerHTML = `<div class="group sp-card"><div class="group-header sp-card__header">Loading</div><div style="padding:12px">Loading player profile…</div></div>`;
	}
}
function renderError(message) {
	if (leaguesEl) {
		leaguesEl.innerHTML = `<div class="group sp-card"><div class="group-header sp-card__header">Error</div><div style="padding:12px">${message || "Failed"}</div></div>`;
	}
}

async function loadPlayer() {
	const q = getQuery();
	const playerId = q.id || q.player || "";
	const season = q.season || String(new Date().getFullYear());
	if (!playerId) {
		if (titleEl) titleEl.textContent = "Player";
		renderError("Missing player id.");
		return;
	}
	if (titleEl) titleEl.textContent = `Player ${playerId} — ${season}`;
	renderLoading();

	const signal = startAbortController();
	try {
		const t0 = nowMs();
		// Get both with de-dup and cache
		const [profileJson, statsJson] = await Promise.all([
			getOrFetchProfile(playerId, signal),
			getOrFetchStats(playerId, season, signal)
		]);

		const fields = extractProfileFields(profileJson, statsJson);
		if (headerRowEl) {
			headerRowEl.innerHTML = `
				${fields.photo ? `<img class="player-photo" loading="lazy" src="${fields.photo}" alt="${fields.name}" style="width:48px;height:48px;object-fit:cover;border-radius:50%">` : ""}
				<div>
					<div style="font-weight:600;font-size:18px;line-height:1.3">${fields.name}</div>
					<div style="color:var(--sp-text-muted)">${fields.position} — ${fields.teamName}</div>
				</div>
			`;
		}
		if (metaEl) {
			metaEl.innerHTML = `
				<div class="sp-kv__label">Age</div><div class="sp-kv__value">${valOrDash(fields.age)}</div>
				<div class="sp-kv__label">Nationality</div><div class="sp-kv__value">${valOrDash(fields.nationality)}</div>
				<div class="sp-kv__label">Height</div><div class="sp-kv__value">${valOrDash(fields.height)}</div>
				<div class="sp-kv__label">Weight</div><div class="sp-kv__value">${valOrDash(fields.weight)}</div>
				<div class="sp-kv__label">Team</div><div class="sp-kv__value">${valOrDash(fields.teamName)}</div>
				<div class="sp-kv__label">Position</div><div class="sp-kv__value">${valOrDash(fields.position)}</div>
			`;
		}

		const leagues = normalizeStatsByLeague(statsJson);
		if (leaguesEl) {
			leaguesEl.innerHTML = "";
			if (leagues.size === 0) {
				const g = document.createElement("div");
				g.className = "group sp-card";
				g.innerHTML = `<div class="group-header sp-card__header">Leagues</div><div style="padding:12px">No stats available</div>`;
				leaguesEl.appendChild(g);
			} else {
				for (const [key, bucket] of leagues) {
					leaguesEl.appendChild(renderAccordionForLeague(key, bucket));
				}
			}
		}

		log("Loaded in", Math.round(nowMs() - t0), "ms");
	} catch (e) {
		if (String(e).includes("AbortError")) return;
		console.error(e);
		renderError(e.message || "Failed");
	}
}

document.addEventListener("DOMContentLoaded", loadPlayer, { once: true });



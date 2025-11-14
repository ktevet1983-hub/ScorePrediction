"use strict";

const $ = (sel) => document.querySelector(sel);

const titleEl = $("#teamTitle");
const contentEl = $("#teamContent");

// Instrumentation & navigation-scoped state
const metrics = {
	navId: String(Date.now()),
	prefetchCalls: 0,
	prefetchDeduped: 0,
	prefetchDurationMs: 0,
	profileCalls: 0,
	profileDeduped: 0,
	profileAborted: 0,
	profilesDurationMs: 0,
	squadDurationMs: 0,
	summaryPrinted: false
};
function nowMs() { return (performance && performance.now) ? performance.now() : Date.now(); }
function log(...args) { try { console.log("[team.js]", ...args); } catch {} }

// Simple in-memory cache so each player's nationality is fetched only once
const nationalityCache = Object.create(null);
let playersPrefetchPromise = null;
// Deduplicate bulk prefetch per team:season
const prefetchCache = new Map();
// Persist cache across navigation
const NAT_VERSION = "v1";
function sessionKey(teamId, season) { return `natCache:${teamId}:${season}:${NAT_VERSION}`; }
// Abort control per navigation
let currentAbortController = null;
function startNavigationController() {
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
// Limit per-player fallback concurrency
const FALLBACK_MAX_CONCURRENCY = 2;
let fallbackActive = 0;
const fallbackQueue = [];
function runNextFallback() {
	if (fallbackActive >= FALLBACK_MAX_CONCURRENCY) return;
	const task = fallbackQueue.shift();
	if (!task) return;
	fallbackActive++;
	task().finally(() => {
		fallbackActive--;
		runNextFallback();
	});
}
function enqueueFallback(fn) {
	return new Promise((resolve) => {
		fallbackQueue.push(async () => {
			const t0 = nowMs();
			try {
				const out = await fn();
				metrics.profilesDurationMs += (nowMs() - t0);
				resolve(out);
			} catch {
				resolve("");
			}
		});
		runNextFallback();
	});
}

function getPlayerIdFrom(playerObj) {
	const directId = playerObj && playerObj.id;
	if (directId != null) return directId;
	const nestedId = playerObj && playerObj.player && playerObj.player.id;
	return nestedId != null ? nestedId : null;
}

function fetchPlayerNationality(playerId, teamId, season, signal) {
	const key = String(playerId);
	if (nationalityCache[key]) {
		metrics.profileDeduped++;
		return nationalityCache[key];
	}

	// If a bulk prefetch is in-flight, wait for it to settle first to avoid per-player storms
	const pfKey = `${teamId}:${season}`;
	const inFlight = prefetchCache.get(pfKey) || playersPrefetchPromise;
	if (inFlight) {
		return Promise.resolve(inFlight).then(() => {
			if (nationalityCache[key]) {
				metrics.profileDeduped++;
				return nationalityCache[key];
			}
			return startPerPlayerFetch(key, signal, teamId, season);
		});
	}

	return startPerPlayerFetch(key, signal, teamId, season);
}

function startPerPlayerFetch(key, signal, teamId, season) {
	if (nationalityCache[key]) {
		metrics.profileDeduped++;
		return nationalityCache[key];
	}

	// Cache the in-flight promise to dedupe concurrent requests
	nationalityCache[key] = enqueueFallback(async () => {
		metrics.profileCalls++;
		try {
			const resp = await fetch(`/profiles?player=${encodeURIComponent(key)}`, { cache: "no-store", signal });
			const text = await resp.text();

			let json;
			try {
				json = JSON.parse(text);
			} catch {
				return "";
			}
			// Try common shapes from API-Football v3
			const nationality =
				(json && json.response && json.response[0] && json.response[0].player && json.response[0].player.nationality) ||
				(json && json.response && json.response[0] && json.response[0].nationality) ||
				"";
			const nat = nationality || "";
			// Persist to session storage for this team/season
			try {
				if (nat && teamId != null && season != null) {
					const keySS = sessionKey(teamId, season);
					const obj = JSON.parse(sessionStorage.getItem(keySS) || "{}");
					obj[String(key)] = nat;
					sessionStorage.setItem(keySS, JSON.stringify(obj));
				}
			} catch {}
			return nat;
		} catch (e) {
			if (e && (e.name === "AbortError" || String(e).includes("AbortError"))) {
				metrics.profileAborted++;
			}
			return "";
		}
	});
	return nationalityCache[key];
}

async function prefetchTeamPlayers(teamId, season, signal) {
	const pfKey = `${teamId}:${season}`;
	const existing = prefetchCache.get(pfKey);
	if (existing) {
		metrics.prefetchDeduped++;
		return existing;
	}
	const t0 = nowMs();
	const promise = (async () => {
		try {
			metrics.prefetchCalls++;
			const url = `/players?team=${encodeURIComponent(teamId)}&season=${encodeURIComponent(season)}`;
			const resp = await fetch(url, { cache: "no-store", signal });
			const text = await resp.text();
			let json;
			try {
				json = JSON.parse(text);
			} catch {
				return;
			}
			const list = Array.isArray(json?.response) ? json.response : [];
			const ssObj = {};
			for (const item of list) {
				const pid = item?.player?.id;
				const nat = item?.player?.nationality;
				if (pid != null && nat && String(nat).trim()) {
					const pidStr = String(pid);
					const natStr = String(nat);
					nationalityCache[pidStr] = Promise.resolve(natStr);
					ssObj[pidStr] = natStr;
				}
			}
			// Persist snapshot
			try {
				const keySS = sessionKey(teamId, season);
				const existingSS = JSON.parse(sessionStorage.getItem(keySS) || "{}");
				sessionStorage.setItem(keySS, JSON.stringify(Object.assign(existingSS, ssObj)));
			} catch {}
			// Optimistically update any rows already rendered
			if (contentEl) {
				const rows = contentEl.querySelectorAll('table tbody tr[data-player-id]');
				for (const tr of rows) {
					const id = tr.getAttribute('data-player-id');
					const cells = tr.querySelectorAll("td");
					const nationalityCell = cells && cells[4];
					const p = nationalityCache[id];
					if (p && nationalityCell) {
						Promise.resolve(p).then((nat) => {
							if (!nationalityCell || !nationalityCell.isConnected) return;
							if (nat && String(nat).trim()) {
								nationalityCell.textContent = String(nat);
							}
						});
					}
				}
			}
		} catch {
			// ignore prefetch errors; fallback will handle
		} finally {
			metrics.prefetchDurationMs += (nowMs() - t0);
		}
	})();
	prefetchCache.set(pfKey, promise);
	return promise;
}

function getQuery() {
	const q = {};
	const s = window.location.search.replace(/^\?/, "");
	for (const part of s.split("&")) {
		if (!part) continue;
		const [k, v = ""] = part.split("=");
		q[decodeURIComponent(k)] = decodeURIComponent(v);
	}
	return q;
}

function setTitle(text) {
	if (titleEl) titleEl.textContent = text || "Team — Season";
}

function renderError(message) {
	contentEl.innerHTML = `<div class="group sp-card"><div class="group-header sp-card__header">Error</div><div style="padding:12px">${message || "Failed"}</div></div>`;
}

function renderSquad(teamName, season, players, teamId) {
	const wrap = document.createElement("div");
	wrap.className = "group sp-card";

	const header = document.createElement("div");
	header.className = "group-header sp-card__header";
	header.textContent = "Squad";
	wrap.appendChild(header);

	const scroll = document.createElement("div");
	scroll.style.overflowX = "auto";
	scroll.style.webkitOverflowScrolling = "touch";

	const table = document.createElement("table");
	table.className = "sp-table";
	table.innerHTML = `
		<thead>
			<tr>
				<th class="mono">#</th>
				<th>Player</th>
				<th>Position</th>
				<th class="mono">Age</th>
				<th>Nationality</th>
			</tr>
		</thead>
		<tbody></tbody>
	`;
	const tbody = table.querySelector("tbody");
	for (let i = 0; i < players.length; i++) {
		const p = players[i] || {};
		const playerName = p.name ?? (p.player && p.player.name) ?? "-";
		const playerPhoto = p.photo ?? (p.player && p.player.photo) ?? "";
		const playerId = getPlayerIdFrom(p);
		const tr = document.createElement("tr");
		if (playerId != null) {
			tr.setAttribute("data-player-id", String(playerId));
		}
		tr.innerHTML = `
			<td class="mono">${p.number ?? "-"}</td>
			<td>
				<div class="team sp-team">
					${playerPhoto ? `<img src="${playerPhoto}" alt="${playerName}" style="width:36px;height:36px;object-fit:cover;border-radius:50%;">` : ""}
					${playerId != null
						? `<a href="/player.html?id=${encodeURIComponent(String(playerId))}&season=${encodeURIComponent(String(season))}" style="text-decoration:none;color:inherit"><span>${playerName}</span></a>`
						: `<span>${playerName}</span>`
					}
				</div>
			</td>
			<td>${p.position ?? "-"}</td>
			<td class="mono">${p.age ?? "-"}</td>
			<td>${(p.nationality && String(p.nationality).trim()) ? p.nationality : "..."}</td>
		`;
		tbody.appendChild(tr);

		// If nationality missing, fetch asynchronously from profiles endpoint and update the cell
		const hasNationality = (p.nationality && String(p.nationality).trim());
		if (!hasNationality) {
			const cells = tr.querySelectorAll("td");
			const nationalityCell = cells && cells[4];
			if (playerId != null && nationalityCell) {
				// If we have a prefilled cache from prefetch, use it; otherwise wait for prefetch before fallback
				const cached = nationalityCache[String(playerId)];
				if (cached) {
					Promise.resolve(cached).then((nat) => {
						if (!nationalityCell || !nationalityCell.isConnected) return;
						nationalityCell.textContent = (nat && String(nat).trim()) ? nat : "-";
					});
					continue;
				}
				// Keep placeholder "..." until loaded; then set to fetched nationality or "-" if unavailable
				const signal = currentAbortController ? currentAbortController.signal : undefined;
				fetchPlayerNationality(playerId, teamId, season, signal).then((nat) => {
					if (!nationalityCell || !nationalityCell.isConnected) return;
					nationalityCell.textContent = (nat && String(nat).trim()) ? nat : "-";
				});
			} else if (nationalityCell) {
				nationalityCell.textContent = "-";
			}
		}
	}
	scroll.appendChild(table);
	wrap.appendChild(scroll);
	contentEl.appendChild(wrap);
}

	async function loadSquad() {
	const navStart = nowMs();
	const q = getQuery();
	const teamId = q.id || q.team || "";
	const season = q.season || String(new Date().getFullYear());
	if (!teamId) {
		setTitle("Team — Season");
		renderError("Missing team id.");
		return;
	}

	try {
		setTitle(`Team ${teamId} — ${season}`);
		contentEl.innerHTML = "";

		const signal = startNavigationController();

		// Hydrate cache from session for fast-first paint
		try {
			const stored = sessionStorage.getItem(sessionKey(teamId, season));
			if (stored) {
				const obj = JSON.parse(stored);
				for (const [pid, nat] of Object.entries(obj)) {
					if (pid && nat) nationalityCache[pid] = Promise.resolve(String(nat));
				}
				log("Hydrated nationalities from sessionStorage:", Object.keys(obj).length);
			}
		} catch {}

		const url = `/squad?team=${encodeURIComponent(teamId)}&season=${encodeURIComponent(season)}`;
		const squadT0 = nowMs();
		const resp = await fetch(url, { cache: "no-store", signal });
		const text = await resp.text();

		let json;
		try {
			json = JSON.parse(text);
		} catch {
			throw new Error("Server did not return valid JSON.");
		}

		if (!resp.ok) {
			const message = json?.error || resp.statusText || "Request failed";
			throw new Error(message);
		}

		const entry = Array.isArray(json?.response) ? json.response[0] : null;
		const teamName = entry?.team?.name || `Team ${teamId}`;
		const teamLogo = entry?.team?.logo || entry?.logo || "";
		const players = Array.isArray(entry?.players) ? entry.players : [];
		if (teamLogo) {
			titleEl.innerHTML = `<div class="team sp-team"><img src="${teamLogo}" alt="${teamName} logo" style="width:50px;height:50px;object-fit:contain;"><span>${teamName} — ${season}</span></div>`;
		} else {
			setTitle(`${teamName} — ${season}`);
		}
		if (players.length === 0) {
			contentEl.innerHTML = `<div class="group sp-card"><div class="group-header sp-card__header">Squad</div><div style="padding:12px">No squad data available.</div></div>`;
			return;
		}
		metrics.squadDurationMs += (nowMs() - squadT0);

		// Kick off a bulk prefetch to populate nationality cache; do not block UI, de-duplicated per team:season
		try {
			playersPrefetchPromise = prefetchTeamPlayers(teamId, season, signal);
			// Ensure we clear the marker when done
			Promise.resolve(playersPrefetchPromise).finally(() => { playersPrefetchPromise = null; });
		} catch {}
		renderSquad(teamName, season, players, teamId);

		// End-of-load summary once prefetch settles
		const pf = prefetchCache.get(`${teamId}:${season}`) || playersPrefetchPromise;
		Promise.resolve(pf).finally(() => {
			if (metrics.summaryPrinted) return;
			metrics.summaryPrinted = true;
			const totalNetwork = metrics.prefetchCalls + metrics.profileCalls;
			let cause = "C"; // default other
			if (metrics.profileCalls > 4 || metrics.profilesDurationMs > metrics.prefetchDurationMs * 2) cause = "A";
			else if (metrics.prefetchCalls > 1 && metrics.prefetchDeduped === 0) cause = "B";
			const saved = Math.max(0, metrics.profileDeduped + metrics.prefetchDeduped);
			log("Load summary =>", {
				navId: metrics.navId,
				causeDetected: cause,
				prefetch: { calls: metrics.prefetchCalls, deduped: metrics.prefetchDeduped, durationMs: Math.round(metrics.prefetchDurationMs) },
				profiles: { calls: metrics.profileCalls, deduped: metrics.profileDeduped, aborted: metrics.profileAborted, durationMs: Math.round(metrics.profilesDurationMs) },
				squadRenderMs: Math.round(metrics.squadDurationMs),
				totalNetworkCalls: totalNetwork,
				requestsSaved: saved
			});
			log("Navigation done in", Math.round(nowMs() - navStart), "ms");
		});
	} catch (err) {
		console.error(err);
		renderError(err.message || "Failed");
	}
}

document.addEventListener("DOMContentLoaded", loadSquad, { once: true });



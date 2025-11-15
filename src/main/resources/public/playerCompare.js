"use strict";

const $ = (sel) => document.querySelector(sel);

// Simple league list (extendable)
const LEAGUES = [
	{ id: 39, name: "Premier League (39)" },
	{ id: 140, name: "La Liga (140)" },
	{ id: 135, name: "Serie A (135)" },
	{ id: 61, name: "Ligue 1 (61)" },
	{ id: 78, name: "Bundesliga (78)" },
	{ id: 383, name: "Ligat Ha'Al (383)" },
	{ id: 2, name: "UEFA Champions League (2)" },
	{ id: 3, name: "UEFA Europa League (3)" },
	{ id: 1, name: "World Cup (1)" }
];

function derivePositionGroup(posRaw) {
	const p = String(posRaw || "").toUpperCase();
	if (!p) return "";
	if (p.includes("GK") || p === "G" || p === "GOALKEEPER") return "GK";
	if (p.startsWith("D")) return "DEF";
	if (p.startsWith("M")) return "MID";
	if (p.startsWith("F") || p.includes("ST") || p.includes("LW") || p.includes("RW")) return "FWD";
	return "";
}

function getPlayerIdFrom(playerObj) {
	const directId = playerObj && playerObj.id;
	if (directId != null) return directId;
	const nestedId = playerObj && playerObj.player && playerObj.player.id;
	return nestedId != null ? nestedId : null;
}

function clearChildren(node) {
	if (!node) return;
	while (node.firstChild) node.removeChild(node.firstChild);
}

function renderErrorCard(container, message) {
	if (!container) return;
	container.innerHTML = `<div class="sp-card"><div class="sp-card__header">Error</div><div style="padding:12px">${message || "Failed"}</div></div>`;
}

function setButtonDisabled(btn, disabled) {
	if (btn) btn.disabled = !!disabled;
}

// Global selection state
let selectedPlayer1 = null;
let selectedPlayer2 = null;
let requiredPositionGroup = null;
let lockedPosition = null;

function updateCompareButton(compareBtn) {
	const bothSelected = !!(selectedPlayer1 && selectedPlayer2);
	const sameGroup = bothSelected && (selectedPlayer1.positionGroup === selectedPlayer2.positionGroup);
	setButtonDisabled(compareBtn, !(bothSelected && sameGroup));
}

function applyPositionFilterToTable(tbody, reqGroup) {
	if (!tbody) return;
	const rows = tbody.querySelectorAll("tr[data-position-group]");
	rows.forEach((tr) => {
		const grp = tr.getAttribute("data-position-group") || "";
		if (!reqGroup || grp === reqGroup) {
			tr.classList.remove("is-disabled");
		} else {
			tr.classList.add("is-disabled");
		}
	});
}

function createRowForPlayer(p, meta) {
	const playerName = p.name ?? (p.player && p.player.name) ?? "-";
	const playerId = getPlayerIdFrom(p);
	const number = p.number ?? (p.player && p.player.number) ?? "-";
	const age = p.age ?? (p.player && p.player.age) ?? "-";
	const position = p.position ?? (p.statistics && p.statistics[0] && p.statistics[0].games && p.statistics[0].games.position) ?? "-";
	const group = derivePositionGroup(position);
	const tr = document.createElement("tr");
	tr.setAttribute("data-player-id", String(playerId || ""));
	tr.setAttribute("data-position", String(position || ""));
	tr.setAttribute("data-position-group", String(group || ""));
	tr.setAttribute("data-team-id", String(meta.teamId));
	tr.setAttribute("data-league-id", String(meta.leagueId));
	tr.setAttribute("data-season", String(meta.season));
	tr.innerHTML = `
		<td>${playerName}</td>
		<td>${position || "-"}</td>
		<td class="mono">${number ?? "-"}</td>
		<td class="mono">${age ?? "-"}</td>
	`;
	return tr;
}

async function fetchTeamsForLeague(leagueId, season, signal) {
	const url = `/standings?season=${encodeURIComponent(String(season))}&league=${encodeURIComponent(String(leagueId))}`;
	const resp = await safeAsync(() => fetch(url, { cache: "no-store", signal }));
	if (!resp) throw new Error("Network error while loading standings.");
	const text = await resp.text();
	let json;
	try {
		json = JSON.parse(text);
	} catch {
		throw new Error("Server did not return valid JSON for standings.");
	}
	if (!resp.ok) {
		const message = json?.error || resp.statusText || "Request failed";
		throw new Error(message);
	}
	// Resiliently gather teams from standings blocks
	const responseArray = Array.isArray(json?.response) ? json.response : (json?.response ? [json.response] : []);
	const first = responseArray[0] || {};
	const league = first.league || {};
	const standings = league.standings || first.standings || [];
	const teams = [];
	const seen = new Set();
	for (const block of standings) {
		if (!Array.isArray(block)) continue;
		for (const row of block) {
			const teamId = row?.team?.id;
			const teamName = row?.team?.name;
			if (teamId != null && teamName && !seen.has(teamId)) {
				seen.add(teamId);
				teams.push({ id: teamId, name: teamName });
			}
		}
	}
	return teams;
}

async function fetchSquad(teamId, season, signal) {
	const url = `/squad?team=${encodeURIComponent(String(teamId))}&season=${encodeURIComponent(String(season))}`;
	const resp = await safeAsync(() => fetch(url, { cache: "no-store", signal }));
	if (!resp) throw new Error("Network error while loading squad.");
	const text = await resp.text();
	let json;
	try {
		json = JSON.parse(text);
	} catch {
		throw new Error("Server did not return valid JSON for squad.");
	}
	if (!resp.ok) {
		const message = json?.error || resp.statusText || "Request failed";
		throw new Error(message);
	}
	const entry = Array.isArray(json?.response) ? json.response[0] : null;
	const players = Array.isArray(entry?.players) ? entry.players : [];
	return players;
}

function setupColumn(idx, compareBtn, onSelectionChanged) {
	const seasonEl = $(`#pc-season-${idx}`);
	const leagueEl = $(`#pc-league-${idx}`);
	const teamEl = $(`#pc-team-${idx}`);
	const table = $(`#pc-squad-table-${idx}`);
	const tbody = table ? table.querySelector("tbody") : null;

	let selectedRow = null;
	let currentLeagueId = "";
	let currentSeason = "";
	let currentTeamId = "";
	// Abort previous in-flight requests per column
	let teamsAbortController = null;
	let squadAbortController = null;

	function clearSelection() {
		if (selectedRow) selectedRow.classList.remove("is-selected");
		selectedRow = null;
		if (idx === 1) selectedPlayer1 = null;
		else selectedPlayer2 = null;
		updateCompareButton(compareBtn);
	}

	function populateLeagues() {
		clearChildren(leagueEl);
		const placeholder = document.createElement("option");
		placeholder.value = "";
		placeholder.textContent = "Choose league";
		placeholder.disabled = true;
		placeholder.selected = true;
		leagueEl.appendChild(placeholder);
		for (const lg of LEAGUES) {
			const opt = document.createElement("option");
			opt.value = String(lg.id);
			opt.textContent = lg.name;
			leagueEl.appendChild(opt);
		}
	}

	async function onLeagueChange() {
		clearSelection();
		currentLeagueId = leagueEl.value || "";
		currentSeason = seasonEl.value || String(new Date().getFullYear());
		teamEl.disabled = true;
		clearChildren(teamEl);
		const placeholder = document.createElement("option");
		placeholder.value = "";
		placeholder.textContent = "Loading teams...";
		placeholder.disabled = true;
		placeholder.selected = true;
		teamEl.appendChild(placeholder);
		try {
			// Abort previous teams request
			if (teamsAbortController) { try { teamsAbortController.abort(); } catch {} }
			teamsAbortController = new AbortController();
			const teams = await fetchTeamsForLeague(currentLeagueId, currentSeason, teamsAbortController.signal);
			clearChildren(teamEl);
			const ph = document.createElement("option");
			ph.value = "";
			ph.textContent = "Choose team";
			ph.disabled = true;
			ph.selected = true;
			teamEl.appendChild(ph);
			for (const t of teams) {
				const opt = document.createElement("option");
				opt.value = String(t.id);
				opt.textContent = t.name;
				teamEl.appendChild(opt);
			}
			teamEl.disabled = false;
		} catch (e) {
			teamEl.disabled = true;
			console.error(e);
			const card = $(`#pc-squad-card-${idx}`);
			if (card) card.insertAdjacentHTML("beforeend", `<div style="padding:12px;color:salmon;">${e.message || "Failed to load teams"}</div>`);
		}
		// Clear squad table
		if (tbody) clearChildren(tbody);
		applyPositionFilterToTable(tbody, requiredPositionGroup);
	}

	async function onTeamChange() {
		clearSelection();
		currentTeamId = teamEl.value || "";
		currentSeason = seasonEl.value || String(new Date().getFullYear());
		if (!currentTeamId) return;
		if (tbody) {
			clearChildren(tbody);
			const loading = document.createElement("tr");
			const td = document.createElement("td");
			td.colSpan = 4;
			td.textContent = "Loading squad…";
			loading.appendChild(td);
			tbody.appendChild(loading);
		}
		try {
			// Abort previous squad request
			if (squadAbortController) { try { squadAbortController.abort(); } catch {} }
			squadAbortController = new AbortController();
			const players = await fetchSquad(currentTeamId, currentSeason, squadAbortController.signal);
			if (tbody) clearChildren(tbody);
			const meta = { teamId: currentTeamId, leagueId: currentLeagueId, season: currentSeason };
			for (const p of players) {
				const tr = createRowForPlayer(p, meta);
				tr.addEventListener("click", () => onRowClick(tr));
				tbody && tbody.appendChild(tr);
			}
			applyPositionFilterToTable(tbody, requiredPositionGroup);
		} catch (e) {
			console.error(e);
			if (tbody) {
				clearChildren(tbody);
				const tr = document.createElement("tr");
				const td = document.createElement("td");
				td.colSpan = 4;
				td.textContent = e.message || "Failed to load squad";
				tr.appendChild(td);
				tbody.appendChild(tr);
			}
		}
	}

	function onRowClick(tr) {
		// If filtered out, ignore
		if (requiredPositionGroup) {
			const grp = tr.getAttribute("data-position-group") || "";
			if (grp !== requiredPositionGroup) return;
		}
		if (tr.classList.contains("is-disabled")) return;

		if (selectedRow) selectedRow.classList.remove("is-selected");
		selectedRow = tr;
		tr.classList.add("is-selected");

		const playerId = tr.getAttribute("data-player-id");
		const teamId = tr.getAttribute("data-team-id");
		const leagueId = tr.getAttribute("data-league-id");
		const season = tr.getAttribute("data-season");
		const position = tr.getAttribute("data-position") || "";
		const group = tr.getAttribute("data-position-group") || derivePositionGroup(position);

		const selected = { playerId, teamId, leagueId, season, positionGroup: group };
		if (idx === 1) selectedPlayer1 = selected; else selectedPlayer2 = selected;

		if (!requiredPositionGroup && group) {
			lockPosition(group);
		}

		// If already set and mismatch, do not accept selection
		if (requiredPositionGroup && group && group !== requiredPositionGroup) {
			// revert selection
			tr.classList.remove("is-selected");
			selectedRow = null;
			if (idx === 1) selectedPlayer1 = null; else selectedPlayer2 = null;
			return;
		}

		onSelectionChanged && onSelectionChanged();
	}

	function onSeasonInput() {
		// When season changes, if league selected, re-fetch teams and clear squad
		if (leagueEl.value) onLeagueChange();
	}

	function init() {
		// Default season current year if empty
		if (seasonEl && !seasonEl.value) seasonEl.value = String(new Date().getFullYear());
		populateLeagues();
		leagueEl.addEventListener("change", onLeagueChange);
		teamEl.addEventListener("change", onTeamChange);
		seasonEl.addEventListener("input", onSeasonInput);
	}

	return {
		init,
		get state() {
			return { leagueId: currentLeagueId, season: currentSeason, teamId: currentTeamId };
		},
		clearSelection
	};
}

function lockPosition(position) {
	requiredPositionGroup = position;
	lockedPosition = position;
	const t1 = $("#pc-squad-table-1 tbody");
	const t2 = $("#pc-squad-table-2 tbody");
	applyPositionFilterToTable(t1, requiredPositionGroup);
	applyPositionFilterToTable(t2, requiredPositionGroup);
}

function unlockPositions() {
	requiredPositionGroup = null;
	lockedPosition = null;
	const t1 = $("#pc-squad-table-1 tbody");
	const t2 = $("#pc-squad-table-2 tbody");
	applyPositionFilterToTable(t1, requiredPositionGroup);
	applyPositionFilterToTable(t2, requiredPositionGroup);
}

function scoreToPercent(t1, t2) {
	const a = Number(t1) || 0;
	const b = Number(t2) || 0;
	const total = a + b;
	if (total <= 0) return { p1: 0, p2: 0 };
	return {
		p1: Math.round((a / total) * 100),
		p2: Math.round((b / total) * 100)
	};
}

function renderResult(container, json) {
	if (!container) return;
	const p1 = json?.player1?.name || "Player 1";
	const p2 = json?.player2?.name || "Player 2";
	const t1 = json?.team1Name || "";
	const t2 = json?.team2Name || "";
	const s1 = Number(json?.scorePlayer1 ?? 0);
	const s2 = Number(json?.scorePlayer2 ?? 0);
	const winner = (json?.winner || "").toUpperCase();
	const pct = scoreToPercent(s1, s2);

	const wrap = document.createElement("div");
	wrap.className = "sp-card";
	const header = document.createElement("div");
	header.className = "sp-card__header";
	header.textContent = "Comparison Result";
	const body = document.createElement("div");
	body.style.padding = "12px";

	let verdict = "It’s too close to call — DRAW";
	if (winner === "PLAYER1") verdict = `We would pick ${p1}`;
	else if (winner === "PLAYER2") verdict = `We would pick ${p2}`;

	const line = `${t1 ? `${t1} — ` : ""}${p1}: ${s1} points (${pct.p1}%), ${t2 ? `${t2} — ` : ""}${p2}: ${s2} points (${pct.p2}%)`;

	let breakdownHtml = "";
	if (Array.isArray(json?.breakdown) && json.breakdown.length) {
		const rows = json.breakdown.map(b => {
			const metric = b?.metric || "-";
			const v1 = b?.p1 ?? "-";
			const v2 = b?.p2 ?? "-";
			const note = b?.note || "";
			return `<tr><td>${metric}</td><td class="mono">${v1}</td><td class="mono">${v2}</td><td>${note}</td></tr>`;
		}).join("");
		breakdownHtml = `
			<div style="margin-top:12px;overflow:auto;">
				<table class="sp-table">
					<thead><tr><th>Metric</th><th>P1</th><th>P2</th><th>Note</th></tr></thead>
					<tbody>${rows}</tbody>
				</table>
			</div>
		`;
	}

	body.innerHTML = `
		<div style="margin-bottom:8px">${line}</div>
		<div><strong>${verdict}</strong></div>
		${breakdownHtml}
	`;
	wrap.appendChild(header);
	wrap.appendChild(body);
	clearChildren(container);
	container.appendChild(wrap);

	try {
		container.scrollIntoView({ behavior: "smooth", block: "start" });
	} catch {}
}

function renderResultError(container, message) {
	if (!container) return;
	const wrap = document.createElement("div");
	wrap.className = "sp-card";
	const header = document.createElement("div");
	header.className = "sp-card__header";
	header.textContent = "Error";
	const body = document.createElement("div");
	body.style.padding = "12px";
	body.textContent = message || "Failed";
	wrap.appendChild(header);
	wrap.appendChild(body);
	clearChildren(container);
	container.appendChild(wrap);
	try { container.scrollIntoView({ behavior: "smooth", block: "start" }); } catch {}
}

async function comparePlayers(resultEl, compareBtn) {
	if (!selectedPlayer1 || !selectedPlayer2) return;
	// Enforce same league & season
	const l1 = String(selectedPlayer1.leagueId || "");
	const l2 = String(selectedPlayer2.leagueId || "");
	const s1 = String(selectedPlayer1.season || "");
	const s2 = String(selectedPlayer2.season || "");
	if (l1 !== l2 || s1 !== s2) {
		renderResultError(resultEl, "Please select players from the same league and season.");
		return;
	}

	const url = `/predict/comparePlayers?league=${encodeURIComponent(l1)}&season=${encodeURIComponent(s1)}&team1=${encodeURIComponent(String(selectedPlayer1.teamId || ""))}&player1=${encodeURIComponent(String(selectedPlayer1.playerId || ""))}&team2=${encodeURIComponent(String(selectedPlayer2.teamId || ""))}&player2=${encodeURIComponent(String(selectedPlayer2.playerId || ""))}`;

	try {
		setButtonDisabled(compareBtn, true);
		// Unlock positions immediately after pressing compare
		unlockPositions();
		clearChildren(resultEl);
		resultEl.innerHTML = `<div class="sp-card"><div class="sp-card__header">Loading</div><div style="padding:12px">Comparing players…</div></div>`;
		const resp = await safeAsync(() => fetch(url, { cache: "no-store" }));
		if (!resp) throw new Error("Network error while comparing players.");
		const text = await resp.text();
		let json;
		try { json = JSON.parse(text); } catch { throw new Error("Server did not return valid JSON."); }
		if (!resp.ok) {
			const message = json?.error || resp.statusText || "Request failed";
			throw new Error(message);
		}
		renderResult(resultEl, json);
	} catch (e) {
		console.error(e);
		renderResultError(resultEl, e.message || "Failed to compare players.");
	} finally {
		updateCompareButton(compareBtn);
	}
}

document.addEventListener("DOMContentLoaded", () => {
	const season1 = $("#pc-season-1");
	const season2 = $("#pc-season-2");
	const league1 = $("#pc-league-1");
	const league2 = $("#pc-league-2");
	const team1 = $("#pc-team-1");
	const team2 = $("#pc-team-2");
	const table1 = $("#pc-squad-table-1 tbody");
	const table2 = $("#pc-squad-table-2 tbody");
	const compareBtn = $("#pc-compare-btn");
	const resultEl = $("#pc-result");

	// Default seasons
	const currentYear = new Date().getFullYear();
	if (season1 && !season1.value) season1.value = String(currentYear);
	if (season2 && !season2.value) season2.value = String(currentYear);

	const onSelectionChanged = () => updateCompareButton(compareBtn);
	const col1 = setupColumn(1, compareBtn, onSelectionChanged);
	const col2 = setupColumn(2, compareBtn, onSelectionChanged);
	col1.init();
	col2.init();

	// Compare button
	compareBtn.addEventListener("click", () => comparePlayers(resultEl, compareBtn));

	// If requiredPositionGroup becomes null again (not in this flow), we would remove filters.
	// For safety: reapply filter when tables change
	const reapplyFilter = () => {
		applyPositionFilterToTable(table1, requiredPositionGroup);
		applyPositionFilterToTable(table2, requiredPositionGroup);
		updateCompareButton(compareBtn);
	};
	league1 && league1.addEventListener("change", () => { requiredPositionGroup = null; reapplyFilter(); });
	league2 && league2.addEventListener("change", () => { requiredPositionGroup = null; reapplyFilter(); });
	season1 && season1.addEventListener("input", () => { requiredPositionGroup = null; reapplyFilter(); });
	season2 && season2.addEventListener("input", () => { requiredPositionGroup = null; reapplyFilter(); });
});




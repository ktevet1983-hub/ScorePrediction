"use strict";

const $ = (sel) => document.querySelector(sel);

const statusEl = $("#status");
const resultsEl = $("#results");
const seasonEl = $("#season");
const leagueEl = $("#league");
const loadBtn = $("#loadBtn");
const team1El = $("#team1");
const team2El = $("#team2");
const predictBtn = $("#predictBtn");
const predictStatusEl = $("#predictStatus");
const groupWrapEl = $("#groupWrap");
const groupEl = $("#group");
let selectedGroup = "";

function setStatus(msg) {
	statusEl.textContent = msg || "";
}

function setPredictStatus(msg) {
	predictStatusEl.textContent = msg || "";
}

function isChampionsLeague(leagueId, leagueName) {
	if (leagueId != null && Number(leagueId) === 2) return true;
	if (leagueName != null && String(leagueName).toLowerCase().includes("champions league")) return true;
	return false;
}

function shouldShowGroup() {
	const season = Number(seasonEl.value || 0);
	const leagueId = leagueEl.value;
	const leagueName = (leagueEl.options[leagueEl.selectedIndex]?.text || "");
	return isChampionsLeague(leagueId, leagueName) && season < 2024;
}

function updateGroupVisibility() {
	const show = shouldShowGroup();
	groupWrapEl.style.display = show ? "" : "none";
	if (!show) {
		// Group not applicable; clear selection and enable team selectors
		selectedGroup = "";
		if (groupEl) groupEl.value = "";
		team1El.disabled = false;
		team2El.disabled = false;
	} else {
		// Group required; if none selected yet, keep placeholder and disable team selectors
		if (!selectedGroup) {
			if (groupEl) groupEl.value = "";
			team1El.disabled = true;
			team2El.disabled = true;
		} else {
			team1El.disabled = false;
			team2El.disabled = false;
		}
	}
	updatePredictButtonDisabled();
}

function clearTeamSelections() {
	team1El.value = "";
	team2El.value = "";
	updatePredictButtonDisabled();
}

function onGroupSelected(group) {
	selectedGroup = group;
	clearTeamSelections();
	// Enable team selectors now that a group is chosen
	team1El.disabled = !selectedGroup;
	team2El.disabled = !selectedGroup;
	updatePredictButtonDisabled();
}

function handleGroupChange(e) {
	const g = e.target.value;
	onGroupSelected(g);
}

function updatePredictButtonDisabled() {
	const requiresGroup = shouldShowGroup();
	const hasGroup = !requiresGroup || !!selectedGroup;
	const hasTeams = !!team1El.value.trim() && !!team2El.value.trim();
	predictBtn.disabled = !(hasGroup && hasTeams);
}
function createTable(groups, groupTitle) {
	const wrapper = document.createElement("div");
	wrapper.className = "group";

	if (groupTitle) {
		const h = document.createElement("div");
		h.className = "group-header";
		h.textContent = groupTitle;
		wrapper.appendChild(h);
	}

	const table = document.createElement("table");
	table.innerHTML = `
		<thead>
			<tr>
				<th class="mono">#</th>
				<th>Team</th>
				<th class="mono">PL</th>
				<th class="mono">W</th>
				<th class="mono">D</th>
				<th class="mono">L</th>
				<th class="mono">GF</th>
				<th class="mono">GA</th>
				<th class="mono">GD</th>
				<th class="mono">Pts</th>
			</tr>
		</thead>
		<tbody></tbody>
	`;
	const tbody = table.querySelector("tbody");
	for (const t of groups) {
		const tr = document.createElement("tr");
		const name = (t.team?.name || "").toString();
		tr.innerHTML = `
			<td class="mono">${t.rank ?? ""}</td>
			<td class="team">${name}</td>
			<td class="mono">${t.all?.played ?? ""}</td>
			<td class="mono">${t.all?.win ?? ""}</td>
			<td class="mono">${t.all?.draw ?? ""}</td>
			<td class="mono">${t.all?.lose ?? ""}</td>
			<td class="mono">${t.all?.goals?.for ?? ""}</td>
			<td class="mono">${t.all?.goals?.against ?? ""}</td>
			<td class="mono">${t.goalsDiff ?? ""}</td>
			<td class="mono">${t.points ?? ""}</td>
		`;
		tbody.appendChild(tr);
	}
	wrapper.appendChild(table);
	return wrapper;
}

function renderStandings(json) {
	resultsEl.innerHTML = "";
	try {
		const res = json?.response?.[0];
		if (!res) {
			resultsEl.textContent = "No data returned.";
			return;
		}
		const league = res.league;
		const title = document.createElement("div");
		title.className = "group-header";
		title.textContent = `${league?.name || "League"} — ${league?.season || ""} (${league?.country || ""})`;
		// Put a top title bar
		const titleWrap = document.createElement("div");
		titleWrap.className = "group";
		titleWrap.appendChild(title);
		resultsEl.appendChild(titleWrap);

		// standings can be: [ [ {team...}, ... ] ] or for cups: [ [groupA...], [groupB...] ... ]
		const standings = league?.standings;
		if (!Array.isArray(standings)) {
			resultsEl.appendChild(document.createTextNode("Unexpected standings format."));
			return;
		}

		for (const block of standings) {
			// block is an array of teams (possibly all same group) or contains group markers
			if (!Array.isArray(block)) continue;

			// Determine group name, if any
			let groupName = null;
			for (const row of block) {
				if (row?.group) {
					groupName = row.group;
					break;
				}
			}
			resultsEl.appendChild(createTable(block, groupName));
		}
	} catch (e) {
		console.error(e);
		resultsEl.textContent = "Failed to render standings.";
	}
}

async function loadStandings() {
	const season = seasonEl.value.trim();
	const league = leagueEl.value.trim();
	if (!season || !league) return;

	try {
		loadBtn.disabled = true;
		setStatus("Loading...");
		resultsEl.innerHTML = "";

		const url = `/standings?season=${encodeURIComponent(season)}&league=${encodeURIComponent(league)}`;
		const resp = await fetch(url);
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
		renderStandings(json);
		setStatus("");
	} catch (err) {
		console.error(err);
		setStatus(err.message || "Failed");
		resultsEl.innerHTML = `<div class="group"><div class="group-header">Error</div><div style="padding:12px">${(err.message || "Unknown error")}</div></div>`;
	} finally {
		loadBtn.disabled = false;
	}
}

loadBtn.addEventListener("click", loadStandings);
document.addEventListener("DOMContentLoaded", () => {
	updateGroupVisibility();
	loadStandings();
	updatePredictButtonDisabled();
});
leagueEl.addEventListener("change", () => { updateGroupVisibility(); updatePredictButtonDisabled(); });
seasonEl.addEventListener("input", () => { updateGroupVisibility(); updatePredictButtonDisabled(); });
team1El.addEventListener("input", updatePredictButtonDisabled);
team2El.addEventListener("input", updatePredictButtonDisabled);

function renderPrediction(json) {
	const wrap = document.createElement("div");
	wrap.className = "group";
	const header = document.createElement("div");
	header.className = "group-header";
	header.textContent = "Prediction";
	const body = document.createElement("div");
	body.style.padding = "12px";

	const t1 = json?.team1Name || "Team 1";
	const t2 = json?.team2Name || "Team 2";
	const s1 = json?.score1;
	const s2 = json?.score2;
	let outcomeText = "Draw";
	if (json?.result === "team1") outcomeText = `${t1} will likely win`;
	if (json?.result === "team2") outcomeText = `${t2} will likely win`;

	body.innerHTML = `
		<div style="margin-bottom:8px"><strong>${t1}</strong> vs <strong>${t2}</strong></div>
		<div style="margin-bottom:8px" class="mono">Score: ${s1} - ${s2}</div>
		<div><strong>${outcomeText}</strong></div>
	`;
	wrap.appendChild(header);
	wrap.appendChild(body);
	resultsEl.appendChild(wrap);
}

async function predict() {
	const season = seasonEl.value.trim();
	const league = leagueEl.value.trim();
	const team1 = team1El.value.trim();
	const team2 = team2El.value.trim();
	if (!season || !league || !team1 || !team2) return;

	try {
		predictBtn.disabled = true;
		setPredictStatus("Computing...");

		const base = `/predict?season=${encodeURIComponent(season)}&league=${encodeURIComponent(league)}&team1=${encodeURIComponent(team1)}&team2=${encodeURIComponent(team2)}`;
		const requiresGroup = shouldShowGroup();
		if (requiresGroup && !selectedGroup) {
			setPredictStatus("Please choose a group.");
			return;
		}
		const groupQuery = requiresGroup && selectedGroup ? `&group=${encodeURIComponent(selectedGroup)}` : "";
		const url = `${base}${groupQuery}`;
		console.log('Predict params:', { league, season, selectedGroup, teamA: team1, teamB: team2 });
		const resp = await fetch(url, { cache: 'no-store' });
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
		renderPrediction(json);
		setPredictStatus("");
	} catch (err) {
		console.error(err);
		setPredictStatus(err.message || "Failed");
	} finally {
		predictBtn.disabled = false;
	}
}

predictBtn.addEventListener("click", predict);
groupEl.addEventListener("change", handleGroupChange);



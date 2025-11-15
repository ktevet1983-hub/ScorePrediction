(async () => {
	const container = document.getElementById("global-nav");
	if (container) {
		const html = await fetch("/partials/nav.html").then(r => r.text());
		container.innerHTML = html;

		// Highlight current tab
		document.querySelectorAll(".sp-nav__tab").forEach(tab => {
			if (tab.href.includes(location.pathname)) {
				tab.classList.add("is-active");
			}
		});
	}
})();



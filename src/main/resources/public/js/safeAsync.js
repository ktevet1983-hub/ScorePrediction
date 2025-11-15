"use strict";

(function (global) {
	/**
	 * Global safe async wrapper for UI fetches and other promises.
	 * Returns the resolved value or null on error, logging the error.
	 * @param {() => Promise<any>} fn
	 * @param {(err: any) => void} [onError]
	 * @returns {Promise<any|null>}
	 */
	async function safeAsync(fn, onError) {
		try {
			return await fn();
		} catch (e) {
			try { console.error("UI async error:", e); } catch {}
			if (onError) {
				try { onError(e); } catch {}
			}
			return null;
		}
	}
	// Expose globally
	global.safeAsync = safeAsync;
})(typeof window !== "undefined" ? window : (typeof globalThis !== "undefined" ? globalThis : this));



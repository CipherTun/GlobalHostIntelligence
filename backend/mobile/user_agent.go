package mobile

// CurrentBrowserUserAgent follows Chrome's reduced Android User-Agent format.
// Chrome 153.0.8010.52 is the stable Android release published by Google on
// 17 September 2026. The reduced form intentionally uses Android 10; K and
// zeroed minor/patch browser components, matching Chrome's UA reduction model.
const CurrentBrowserUserAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Mobile Safari/537.36"

func effectiveUserAgent(value string) string {
	if value == "" || len(value) >= len("GlobalHostIntelligence/") && value[:len("GlobalHostIntelligence/")] == "GlobalHostIntelligence/" {
		return CurrentBrowserUserAgent
	}
	return value
}

package mobile

import (
    "fmt"
    "net/http"
    "strings"
)

// urlscanCountrySource is a dedicated, keyless country-scoped discovery source.
// It is intentionally separate from the country/RIPEstat engine so a slow
// routing lookup cannot block URLScan country observations.
func urlscanCountrySource(c *http.Client, country string, limit int) ([]string, error) {
    country = strings.TrimSpace(strings.ToLower(country))
    if len(country) != 2 {
        return nil, fmt.Errorf("country must be ISO-3166 alpha-2")
    }
    return urlscanCountry(c, country, limit)
}

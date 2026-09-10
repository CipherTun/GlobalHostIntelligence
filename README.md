# Global Host Intelligence

Global Host Intelligence (GHI) is an Android network-intelligence toolkit for discovering, inspecting, and analyzing internet hosts and domains from a clean mobile interface.

## Features

### Host Discovery
- Discover publicly available hosts and domains.
- Organize discovered results for easier inspection.
- View individual domain details instead of dumping everything into one screen.
- Continue working with cached results when appropriate.

### Subdomain Discovery
- Find subdomains associated with a target domain.
- Inspect discovered hosts individually.
- Use the results as input for further host analysis.

### Response Checker
- Check HTTP and HTTPS endpoints.
- Supports GET, HEAD, POST, PUT, PATCH, and OPTIONS.
- Inspect status codes, redirects, headers, TLS information, CDN information, and response previews.
- Configure request path, headers, body, timeout, redirect handling, and insecure TLS handling where supported.

### IP / Domain Intelligence
- Inspect IP and domain relationships.
- Analyze network information and host details.
- Use domain and IP results as starting points for additional investigation.

### Payload Generator
- Build request payloads from the app interface.
- Configure the request path, headers, body, and related request parameters.
- Generate payloads for legitimate testing and diagnostics.

### Network Diagnostics
- DNS and TLS-oriented diagnostics are available through the application's deeper analysis flows.
- Certificate and connection information can be inspected when supported by the target.

### Mobile Core
- The discovery engine is compiled into the Android application through the project's native mobile core.
- The release build supports the native ARM configurations provided by the project.

### Modern Android UI
- Material 3 interface.
- Bottom navigation for the primary tools.
- Navigation drawer for the complete tool list.
- Dark network-intelligence visual design.
- Animated/visual app presentation without requiring a terminal workflow.

### Advertising
- Production AdMob banner advertising.
- Automatic rewarded-interstitial advertising at controlled intervals.
- Ads are kept separate from Settings and core navigation.
- Debug builds use Google's test advertising configuration; release builds use the production configuration.

## How to use GHI

1. Open GHI.
2. Choose a tool from the bottom navigation or open the navigation drawer.
3. Enter the domain, host, IP address, or request information required by the selected tool.
4. Start the operation.
5. Review the results in the app's result interface.
6. Tap an individual domain or host when you need deeper details.
7. Use the Response Checker when you need to inspect how an HTTP/HTTPS endpoint responds.
8. Use IP / Domain when you need network or host intelligence.
9. Use Payload Generator when you need to construct a request payload for authorized testing.
10. Open Settings for application preferences and configuration.

## Recommended workflow

For a new investigation, start with **Discovery**, review the returned domains, open an individual result for details, then use **Subdomains**, **IP / Domain**, or **Response Checker** for deeper analysis.

Only analyze hosts, domains, and services that you own or are authorized to test.

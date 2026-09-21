# Local gateway discovery v1

IPv4 UDP port 8098. All datagrams are ASCII. Discovery is optional and unauthenticated;
manual HTTP/HTTPS URL pairing remains supported. This is not a receiver list or mDNS.

Request: `ZOMBIE_DISCOVER_V1 <32 lowercase hex nonce>\n`

Response: `ZOMBIE_GATEWAY_V1 <same nonce> <HTTP port 1..65535>\n`

The scanner derives the HTTP host from the packet's IPv4 source, never a supplied
URL. Accept only private/link-local/loopback sources, matching nonce and source
port 8098. Nonce correlation limits accidental/stale replies; it is not authentication.
Never send saved device credentials to discovered candidates automatically.

One scan has a 2.5-second monotonic receive budget, at most 64 packets, 16 candidates
and 16 broadcast destinations. Use directed interface broadcasts plus the limited
broadcast address; no subnet sweeps, remote DNS or unbounded retry. Read timeouts
are 200ms. UI disposal suppresses late delivery; the socket closes when the bounded
scan finishes or worker interruption is observed. Discovery failure does not alter
existing pairing or stop media sessions. The Go responder bounds replies to 20/s
and closes on shutdown; it has no access to account, token or pairing secrets.

First delivery uses IPv4 trusted LANs. IPv6/mDNS and identity-authenticated DHCP
reconnection remain separate work. No new HTTP API version or QR contract is implied.

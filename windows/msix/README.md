# Microsoft Store MSIX

Store display name: **Marantz AV Remote**. Store ID: `9NZWS7Q1JHMB`.
Package identity: `throy.MarantzAVRemote`. Package family: `throy.MarantzAVRemote_4st3013e5ncry`.

The Windows Store MSIX workflow packages the tested Windows executable as version `1.2.1.0`, using the identity assigned by Partner Center. Package identity values are public metadata, not signing credentials. The icon uses the m extracted from the existing Marantz wordmark.

The resulting `.msix` is unsigned and intended for Partner Center upload. It is not a replacement for a publicly signed direct-download installer. Microsoft signs the approved Store package. Package generation does not submit or publish the app.

The package uses `runFullTrust` because it runs a Python desktop executable with a local HTTP server and LAN receiver connection. Explain this capability during Store submission. WebView2 Runtime remains required. Test installation, launch, loopback WebView access, and receiver discovery on Windows before publishing.

For a new release, increase the package version. The workflow builds the executable from the current source. The package manifest is generated and validated by Windows SDK MakeAppx. Version numbers must increase for later Store submissions.

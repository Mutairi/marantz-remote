# Microsoft Store MSIX

Reserved name: **Marantz Remote by THROY**. Store ID: `9MX0GQ4F3W0B`.

The Windows Store MSIX workflow packages the tested Windows executable as version `1.2.1.0`, using the identity assigned by Partner Center. Package identity values are public metadata, not signing credentials. A THROY gold-ring icon is generated during packaging.

The resulting `.msix` is unsigned and intended for Partner Center upload. It is not a replacement for a publicly signed direct-download installer. Microsoft signs the approved Store package. Package generation does not submit or publish the app.

The package uses `runFullTrust` because it runs a Python desktop executable with a local HTTP server and LAN receiver connection. Explain this capability during Store submission. WebView2 Runtime remains required. Test installation, launch, loopback WebView access, and receiver discovery on Windows before publishing.

For a new release, update the workflow's source executable run and package version; source artifacts expire after 30 days. The package manifest is generated and validated by Windows SDK MakeAppx. Version numbers must increase for later Store submissions.

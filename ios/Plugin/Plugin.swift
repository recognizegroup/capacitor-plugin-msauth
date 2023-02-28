import Foundation
import Capacitor
import MSAL

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(MsAuthPlugin)
public class MsAuthPlugin: CAPPlugin {
    @objc func login(_ call: CAPPluginCall) {
        guard let context = createContextFromPluginCall(call) else {
            call.reject("Unable to create context, check logs")
            return
        }

        let scopes = call.getArray("scopes", String.self) ?? []

        let completion: (MSALResult?) -> Void = { msalResult in
            guard let result = msalResult else {
                call.reject("Unable to obtain access token")
                return
            }

            call.resolve([
                "accessToken": result.accessToken,
                "idToken": result.idToken,
                "scopes": result.scopes
            ])
        }

        loadCurrentAccount(applicationContext: context) { (account) in
            guard let currentAccount = account else {
                self.acquireTokenInteractively(applicationContext: context, scopes: scopes, completion: completion)
                return
            }

            self.acquireTokenSilently(applicationContext: context, scopes: scopes, account: currentAccount, completion: completion)
        }
    }

    @objc func logout(_ call: CAPPluginCall) {
        guard let context = createContextFromPluginCall(call) else {
            call.reject("Unable to create context, check logs")
            return
        }

        guard let bridgeViewController = bridge?.viewController else {
            call.reject("Unable to get Capacitor bridge.viewController")
            return
        }

        loadCurrentAccount(applicationContext: context) { (account) in
            guard let currentAccount = account else {
                call.reject("Nothing to sign-out from.")
                return
            }

            do {
                let wvParameters = MSALWebviewParameters(authPresentationViewController: bridgeViewController)
                let signoutParameters = MSALSignoutParameters(webviewParameters: wvParameters)
                signoutParameters.signoutFromBrowser = false // set this to true if you also want to signout from browser or webview

                context.signout(with: currentAccount, signoutParameters: signoutParameters, completionBlock: {(_, error) in
                    if let error = error {
                        print("Unable to logout: \(error)")

                        call.reject("Unable to logout")

                        return
                    }

                    call.resolve()
                })
            }
        }
    }

    private func createContextFromPluginCall(_ call: CAPPluginCall) -> MSALPublicClientApplication? {
        guard let clientId = call.getString("clientId") else {
            call.reject("Invalid client ID specified.")
            return nil
        }
        let domainHint = call.getString("domainHint")
        let tenant = call.getString("tenant")
        let authorityURL = call.getString("authorityUrl")
        let authorityType = call.getString("authorityType") ?? "AAD"

        if authorityType != "AAD" && authorityType != "B2C" {
            call.reject("authorityType must be one of 'AAD' or 'B2C'")
            return nil
        }

        guard let enumAuthorityType = AuthorityType(rawValue: authorityType.lowercased()),
              let context = createContext(
                clientId: clientId, domainHint: domainHint, tenant: tenant, authorityType: enumAuthorityType, customAuthorityURL: authorityURL
              ) else {
            call.reject("Unable to create context, check logs")
            return nil
        }

        return context
    }

    private func createContext(clientId: String, domainHint: String?, tenant: String?, authorityType: AuthorityType, customAuthorityURL: String?) -> MSALPublicClientApplication? {
        guard let authorityURL = URL(string: customAuthorityURL ?? "https://login.microsoftonline.com/\(tenant ?? "common")") else {
            print("Invalid authorityUrl or tenant specified")
            return nil
        }

        do {
            let authority = authorityType == .aad
                ? try MSALAADAuthority(url: authorityURL) : try MSALB2CAuthority(url: authorityURL)

            if domainHint != nil {
                print("Warning: domain hint is currently not supported on iOS.")
            }

            let msalConfiguration = MSALPublicClientApplicationConfig(clientId: clientId, redirectUri: nil, authority: authority)
            msalConfiguration.knownAuthorities = [authority]
            return try MSALPublicClientApplication(configuration: msalConfiguration)
        } catch {
            print(error)

            return nil
        }
    }

    typealias AccountCompletion = (MSALAccount?) -> Void

    func loadCurrentAccount(applicationContext: MSALPublicClientApplication, completion: @escaping AccountCompletion) {
        let msalParameters = MSALParameters()
        msalParameters.completionBlockQueue = DispatchQueue.main

        // Check through multiple accounts in the cache if present
        do {
            let accounts = try applicationContext.allAccounts() // Get all cached accounts
            if accounts.count > 1 {
                let authorityUrl = applicationContext.configuration.authority.url
                for account in accounts {
                    if let tenants = account.tenantProfiles {
                        for tenant in tenants {
                            if let tenantId = tenant.tenantId {
                                // Find first account where authority url matches tenant id
                                if authorityUrl.absoluteString.contains(tenantId) { 
                                    completion(account)
                                    return
                                }
                            }
                        }
                    }
                }
                // If no match is found for the authority url (fallback for multi-tenant app registration)
                completion(accounts[0]) // return the first available account
                return
            }
        } catch {
            print("Unable to access cached accounts list")
        }


        applicationContext.getCurrentAccount(with: msalParameters, completionBlock: { (currentAccount, _, error) in
            if let error = error {
                print("Unable to query current account: \(error)")

                completion(nil)

                return
            }

            if let currentAccount = currentAccount {
                completion(currentAccount)

                return
            }

            completion(nil)
        })

    }

    func acquireTokenInteractively(applicationContext: MSALPublicClientApplication, scopes: [String], completion: @escaping (MSALResult?) -> Void) {
        guard let bridgeViewController = bridge?.viewController else {
            print("Unable to get Capacitor bridge.viewController")

            completion(nil)
            return
        }

        let wvParameters = MSALWebviewParameters(authPresentationViewController: bridgeViewController)
        let parameters = MSALInteractiveTokenParameters(scopes: scopes, webviewParameters: wvParameters)

        parameters.promptType = .selectAccount

        applicationContext.acquireToken(with: parameters) { (result, error) in
            if let error = error {
                print("Token could not be acquired: \(error)")

                completion(nil)
                return
            }

            guard let result = result else {
                print("Empty result found.")

                completion(nil)
                return
            }

            completion(result)
        }
    }

    func acquireTokenSilently(applicationContext: MSALPublicClientApplication, scopes: [String], account: MSALAccount, completion: @escaping (MSALResult?) -> Void) {
        let parameters = MSALSilentTokenParameters(scopes: scopes, account: account)

        applicationContext.acquireTokenSilent(with: parameters) { (result, error) in

            if let error = error {
                let nsError = error as NSError

                if nsError.domain == MSALErrorDomain {

                    if nsError.code == MSALError.interactionRequired.rawValue {
                        DispatchQueue.main.async {
                            self.acquireTokenInteractively(applicationContext: applicationContext, scopes: scopes, completion: completion)
                        }
                        return
                    }
                }

                print("Unable to acquire token silently: \(error)")

                completion(nil)

                return
            }

            guard let result = result else {
                print("Empty result found.")

                completion(nil)
                return
            }

            completion(result)
        }
    }

    public static func checkAppOpen(url: URL, options: [UIApplication.OpenURLOptionsKey: Any] = [:]) -> Bool {
        MSALPublicClientApplication.handleMSALResponse(
            url, sourceApplication: options[UIApplication.OpenURLOptionsKey.sourceApplication] as? String
        )
    }
}

enum AuthorityType: String {
    case aad
    case b2c
}

extension UIApplicationDelegate {
    func application(_ app: UIApplication, open url: URL, options: [UIApplication.OpenURLOptionsKey: Any] = [:]) -> Bool {
        MsAuthPlugin.checkAppOpen(url: url, options: options)
    }
}

@available(iOS 13.0, *)
extension UISceneDelegate {
    func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
        guard let urlContext = URLContexts.first else {
            return
        }

        let url = urlContext.url
        let sourceApp = urlContext.options.sourceApplication

        MSALPublicClientApplication.handleMSALResponse(url, sourceApplication: sourceApp)
    }
}

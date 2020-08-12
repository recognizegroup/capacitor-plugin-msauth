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
        guard let context = self.createContextFromPluginCall(call) else {
            call.reject("Unable to create context, check logs")
            return
        }
        
        let scopes = call.getArray("scopes", String.self) ?? []
        
        let completion: (String?) -> Void = { accessToken in
            guard let at = accessToken else {
                call.reject("Unable to obtain access token")
                return
            }
            
            call.resolve(["accessToken": at])
        }
        
        self.loadCurrentAccount(applicationContext: context) { (account) in
            guard let currentAccount = account else {
                self.acquireTokenInteractively(applicationContext: context, scopes: scopes, completion: completion)
                return
            }
            
            self.acquireTokenSilently(applicationContext: context, scopes: scopes, account: currentAccount, completion: completion)
        }
    }
    
    @objc func logout(_ call: CAPPluginCall) {
        guard let context = self.createContextFromPluginCall(call) else {
            call.reject("Unable to create context, check logs")
            return
        }
        
        self.loadCurrentAccount(applicationContext: context) { (account) in
            guard let currentAccount = account else {
                call.reject("Nothing to sign-out from.")
                return
            }
            
            do {
                let wvParameters = MSALWebviewParameters(authPresentationViewController: self.bridge.viewController)
                let signoutParameters = MSALSignoutParameters(webviewParameters: wvParameters)
                signoutParameters.signoutFromBrowser = false // set this to true if you also want to signout from browser or webview
                
                context.signout(with: currentAccount, signoutParameters: signoutParameters, completionBlock: {(success, error) in
                    if let error = error {
                        print("Unable to logout: \(error)")
                        
                        call.reject("Unable to lgout")
                        
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
        let tenant = call.getString("tenant")
        
        guard let context = self.createContext(clientId: clientId, tenant: tenant) else {
            call.reject("Unable to create context, check logs")
            return nil
        }
        
        return context
    }
    
    private func createContext(clientId: String, tenant: String?) -> MSALPublicClientApplication? {
        guard let authorityURL = URL(string: "https://login.microsoftonline.com/\(tenant ?? "common")") else {
            print("Invalid tenant specified")
            
            return nil
        }
        
        do {
            let authority = try MSALAADAuthority(url: authorityURL)
            
            let msalConfiguration = MSALPublicClientApplicationConfig(clientId: clientId, redirectUri: nil, authority: authority)
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
                
        applicationContext.getCurrentAccount(with: msalParameters, completionBlock: { (currentAccount, previousAccount, error) in
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
    
    func acquireTokenInteractively(applicationContext: MSALPublicClientApplication, scopes: [String], completion: @escaping (String?) -> Void) {
        let wvParameters = MSALWebviewParameters(authPresentationViewController: self.bridge.viewController)
            
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
                
            completion(result.accessToken)
        }
    }
    
    func acquireTokenSilently(applicationContext: MSALPublicClientApplication, scopes: [String], account : MSALAccount!, completion: @escaping (String?) -> Void) {
           let parameters = MSALSilentTokenParameters(scopes: scopes, account: account)
           
           applicationContext.acquireTokenSilent(with: parameters) { (result, error) in
               
               if let error = error {
                   let nsError = error as NSError

                   if (nsError.domain == MSALErrorDomain) {
                       
                       if (nsError.code == MSALError.interactionRequired.rawValue) {
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
                   
               completion(result.accessToken)
           }
       }
}

extension UIApplicationDelegate {
    func application(_ app: UIApplication, open url: URL, options: [UIApplication.OpenURLOptionsKey : Any] = [:]) -> Bool {
            
            return MSALPublicClientApplication.handleMSALResponse(url, sourceApplication: options[UIApplication.OpenURLOptionsKey.sourceApplication] as? String)
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

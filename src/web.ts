import {WebPlugin} from '@capacitor/core';
import {BaseOptions, MsAuthPlugin} from './definitions';
import {PublicClientApplication} from "@azure/msal-browser";

interface WebBaseOptions extends BaseOptions {
  redirectUri?: string,
}

interface WebLoginOptions extends WebBaseOptions {
  scopes: string[];
}

interface WebLogoutOptions extends WebBaseOptions {
}

interface AuthResult {
  accessToken: string;
  idToken: string;
  scopes: string[];
}

export class MsAuthPluginWeb extends WebPlugin implements MsAuthPlugin {
  constructor() {
    super({
      name: 'MsAuthPlugin',
      platforms: ['web'],
    });
  }

  async login(options: WebLoginOptions): Promise<AuthResult> {
    const context = this.createContext(options);

    try {
      return await this.acquireTokenSilently(context, options.scopes)
          .catch(() => this.acquireTokenInteractively(context, options.scopes));
    } catch (error) {
      console.error('MSAL: Error occurred while logging in', error);

      throw error;
    }
  }

  logout(options: WebLogoutOptions): Promise<void> {
    const context = this.createContext(options);

    if (!context.getAllAccounts()[0]) {
      return Promise.reject('Nothing to sign out from.');
    } else {
      return context.logout();
    }
  }

  private createContext(options: WebBaseOptions) {
    const config = {
      auth: {
        clientId: options.clientId,
        authority: options.authorityUrl ?? `https://login.microsoftonline.com/${options.tenant ?? 'common'}`,
        knownAuthorities: options.knownAuthorities,
        redirectUri: options.redirectUri ?? this.getCurrentUrl()
      },
      cache: {
        cacheLocation: 'localStorage'
      }
    };

    return new PublicClientApplication(config);
  }

  private getCurrentUrl(): string {
    return window.location.href.split(/[?#]/)[0];
  }

  private async acquireTokenInteractively(context: PublicClientApplication, scopes: string[]): Promise<AuthResult> {
    const {accessToken, idToken} = await context.acquireTokenPopup({
      scopes,
      prompt: 'select_account',
    });

    return {accessToken, idToken, scopes};
  }

  private async acquireTokenSilently(context: PublicClientApplication, scopes: string[]): Promise<AuthResult> {
    const {accessToken, idToken} = await context.acquireTokenSilent({
      scopes,
      account: context.getAllAccounts()[0],
    });

    return {accessToken, idToken, scopes};
  }
}

const MsAuthPlugin = new MsAuthPluginWeb();

export {MsAuthPlugin};

import { WebPlugin } from '@capacitor/core';
import { MsAuthPluginPlugin } from './definitions';

interface BaseOptions { clientId: string; tenant?: string; scopes?: string[], keyHash?: string }
interface LogoutOptions extends BaseOptions {
}
interface LoginOptions extends BaseOptions {
  scopes: string[];
}

export class MsAuthPluginWeb extends WebPlugin implements MsAuthPluginPlugin {
  constructor() {
    super({
      name: 'MsAuthPlugin',
      platforms: ['ios', 'android', 'web'],
    });
  }

  async login(options: LoginOptions): Promise<{ accessToken: string }> {
    const context = this.createContext(options);

    try {
      return { accessToken: await this.acquireTokenSilently(context, options.scopes) };
    } catch (error) {
      console.error('MSAL: Error occurred while logging in', error);

      throw error;
    }
  }

  logout(options: LogoutOptions): Promise<void> {
    const context = this.createContext(options);

    if (context.getActiveAccount() == null) {
      return Promise.reject('Nothing to sign out from.');
    } else {
      return context.logout();
    }
  }

  private createContext(options: BaseOptions) {
    const config = {
      auth: {
        clientId: options.clientId,
        authority: `https://login.microsoftonline.com/${options.tenant ?? 'common'}`
      }
    };

    return new PublicClientApplication(config);
  }

  private async acquireTokenInteractively(context: PublicClientApplication, scopes: string[]): Promise<string> {
    const result = await context.acquireTokenPopup({
      scopes,
      prompt: 'select_account',
    });

    return result.accessToken;
  }

  private async acquireTokenSilently(context: PublicClientApplication, scopes: string[]): Promise<string> {
    if (context.getActiveAccount() == null) {
      return this.acquireTokenInteractively(context, scopes);
    } else {
      const result = await context.acquireTokenSilent({
        scopes,
      });

      return result.accessToken;
    }
  }
}

const MsAuthPlugin = new MsAuthPluginWeb();

export { MsAuthPlugin };

import { registerWebPlugin } from '@capacitor/core';
import {PublicClientApplication} from "@azure/msal-browser";
registerWebPlugin(MsAuthPlugin);

import { WebPlugin } from '@capacitor/core';
import { MsAuthPluginPlugin } from './definitions';

export class MsAuthPluginWeb extends WebPlugin implements MsAuthPluginPlugin {
  constructor() {
    super({
      name: 'MsAuthPlugin',
      platforms: ['ios'], // TODO: change this when more platforms supported
    });
  }

  login(options: { clientId: string; tenant?: string; scopes?: string[] }): Promise<{ accessToken: string }> {
    return Promise.reject(`Not implemented for web at this point (options: ${JSON.stringify(options)})`);
  }

  logout(options: { clientId: string; tenant?: string }): Promise<{ accessToken: string }> {
    return Promise.reject(`Not implemented for web at this point (options: ${JSON.stringify(options)})`);
  }
}

const MsAuthPlugin = new MsAuthPluginWeb();

export { MsAuthPlugin };

import { registerWebPlugin } from '@capacitor/core';
registerWebPlugin(MsAuthPlugin);

import {registerPlugin} from '@capacitor/core';
import {MsAuthPluginWeb} from './web';

import type {MsAuthPluginPlugin} from './definitions';

const MsAuthPlugin = registerPlugin<MsAuthPluginPlugin>('MsAuthPlugin', {
    web: () => new MsAuthPluginWeb(),
});

export * from './definitions';
export { MsAuthPlugin };

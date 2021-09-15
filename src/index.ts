import {registerPlugin} from '@capacitor/core';
import {MsAuthPluginWeb} from './web';

import type {MsAuthPlugin as PluginDefinition} from './definitions';

const MsAuthPlugin = registerPlugin<PluginDefinition>('MsAuthPlugin', {
    web: () => new MsAuthPluginWeb(),
});

export * from './definitions';
export { MsAuthPlugin };

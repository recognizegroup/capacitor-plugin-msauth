import { registerPlugin } from '@capacitor/core';

import type { MsAuthPlugin as PluginDefinition } from './definitions';
import { MsAuth } from './web';

const MsAuthPlugin = registerPlugin<PluginDefinition>('MsAuthPlugin', {
  web: () => new MsAuth(),
});

export * from './definitions';
export { MsAuthPlugin };

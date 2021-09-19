import { registerPlugin } from '@capacitor/core';
import { MsAuth } from './web';
const MsAuthPlugin = registerPlugin('MsAuthPlugin', {
    web: () => new MsAuth(),
});
export * from './definitions';
export { MsAuthPlugin };
//# sourceMappingURL=index.js.map
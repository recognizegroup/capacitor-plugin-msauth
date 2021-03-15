declare module '@capacitor/core' {
  interface PluginRegistry {
    MsAuthPlugin: MsAuthPluginPlugin;
  }
}

export interface MsAuthPluginPlugin {
  login(options: { clientId: string, tenant?: string, scopes?: string[], keyHash?: string }): Promise<{ accessToken: string }>;
  logout(options: { clientId: string, tenant?: string, keyHash?: string }): Promise<void>;
}

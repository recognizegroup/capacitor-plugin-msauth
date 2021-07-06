export interface BaseOptions {
  clientId: string;
  tenant?: string;
  authorityType?: 'AAD' | 'B2C';
  authorityUrl?: string;
  knownAuthorities?: string[];
  keyHash?: string;
}

export interface LoginOptions extends BaseOptions {
  scopes?: string[];
}

export interface LogoutOptions extends BaseOptions {
}

export interface MsAuthPluginPlugin {
  login(options: LoginOptions): Promise<{ accessToken: string }>;

  logout(options: LogoutOptions): Promise<void>;
}

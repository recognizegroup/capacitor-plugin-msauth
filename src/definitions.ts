export interface BaseOptions {
  clientId: string;
  tenant?: string;
  domainHint?: string;
  authorityType?: 'AAD' | 'B2C';
  authorityUrl?: string;
  knownAuthorities?: string[];
  keyHash?: string;
  brokerRedirectUriRegistered?: boolean;
}

export interface LoginOptions extends BaseOptions {
  scopes?: string[];
}

export type LogoutOptions = BaseOptions;

export interface MsAuthPlugin {
  login(options: LoginOptions): Promise<{ accessToken: string; idToken: string; scopes: string[] }>;
  logout(options: LogoutOptions): Promise<void>;
  logoutAll(options: LogoutOptions): Promise<void>;
}

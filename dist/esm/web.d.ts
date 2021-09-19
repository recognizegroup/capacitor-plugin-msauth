import { WebPlugin } from '@capacitor/core';
import type { BaseOptions, MsAuthPlugin } from './definitions';
interface WebBaseOptions extends BaseOptions {
    redirectUri?: string;
}
interface WebLoginOptions extends WebBaseOptions {
    scopes: string[];
}
declare type WebLogoutOptions = WebBaseOptions;
interface AuthResult {
    accessToken: string;
    idToken: string;
    scopes: string[];
}
export declare class MsAuth extends WebPlugin implements MsAuthPlugin {
    login(options: WebLoginOptions): Promise<AuthResult>;
    logout(options: WebLogoutOptions): Promise<void>;
    private createContext;
    private getCurrentUrl;
    private acquireTokenInteractively;
    private acquireTokenSilently;
}
export {};

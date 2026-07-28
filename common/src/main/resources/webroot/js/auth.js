import { api } from "./api.js";

/**
 * Sign-in state: the pairing-code screen and the signed-in profile.
 *
 * Players get a code from `/colonyweb sync` in-game; there is no password to manage here.
 */
export function authState() {
    return {
        /** null until /auth/me has answered — the page shows a loader meanwhile. */
        session: null,
        loginCode: "",
        loginError: "",
        loggingIn: false,

        get authReady() {
            return this.session !== null;
        },

        get signedIn() {
            return !!this.session && this.session.authenticated;
        },

        get profile() {
            return (this.session && this.session.user) || null;
        },

        /** Ask the server who we are. Never throws — a failure just shows the sign-in screen. */
        async loadSession() {
            try {
                this.session = await api.session();
            } catch (e) {
                this.session = { authEnabled: true, authenticated: false };
            }
            return this.signedIn;
        },

        /** Codes are typed as XXXX-XXXX; accept any casing and re-insert the dash. */
        formatCode() {
            const raw = this.loginCode.replace(/[^A-Za-z0-9]/g, "").toUpperCase().slice(0, 8);
            this.loginCode = raw.length > 4 ? `${raw.slice(0, 4)}-${raw.slice(4)}` : raw;
        },

        async submitLogin() {
            if (this.loggingIn) return;
            this.loginError = "";
            this.loggingIn = true;
            try {
                const res = await api.login(this.loginCode.trim());
                if (!res.ok) {
                    this.loginError = res.data.error || "That code was not accepted.";
                    return;
                }
                this.session = res.data;
                this.loginCode = "";
                await this.startDashboard();
            } catch (e) {
                this.loginError = "Could not reach the server. Is it still running?";
            } finally {
                this.loggingIn = false;
            }
        },

        async signOut() {
            try {
                await api.logout();
            } catch (e) {
                // Even if the call fails, drop local state so the viewer is not stuck.
            }
            this.session = { authEnabled: true, authenticated: false };
            this.colonies = [];
            this.colonyId = null;
            this.closeEvents();
        },

        /** Called when the server rejects a request mid-session (expired cookie, /colonyweb logout). */
        onUnauthorized() {
            this.session = { authEnabled: true, authenticated: false };
            this.closeEvents();
        },
    };
}

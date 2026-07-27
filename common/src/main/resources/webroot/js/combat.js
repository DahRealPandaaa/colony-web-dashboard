/**
 * Combat tab helpers. The payload is already summarised server-side, so this only handles
 * presentation decisions.
 */
export function combatState() {
    return {
        /** Staffing indicator colour for a guard post. */
        postStatus(post) {
            if (post.assigned >= post.capacity) return "ok";
            return post.assigned > 0 ? "deliver" : "missing";
        },

        guardHealthPct(guard) {
            return guard.maxHealth ? (guard.health / guard.maxHealth) * 100 : 0;
        },

        get raidHeadline() {
            if (!this.combat) return "";
            if (this.combat.underAttack) return "The colony is under attack";
            return this.combat.raidsPossible ? "No active raid" : "Raids are disabled for this colony";
        },
    };
}

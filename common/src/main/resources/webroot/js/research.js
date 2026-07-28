/**
 * Research tab: branch selection and state filtering over the cached research tree.
 */
export function researchState() {
    return {
        researchBranch: "",
        researchFilter: "all",

        get researchBranches() {
            const branches = (this.research && this.research.branches) || [];
            if (!this.researchBranch) return branches;
            return branches.filter((branch) => branch.id === this.researchBranch);
        },

        researchIn(branch) {
            if (this.researchFilter === "all") return branch.researches;
            return branch.researches.filter((entry) => entry.state === this.researchFilter);
        },

        /** Anything the university is actively working on, across all branches. */
        get researchInProgress() {
            const branches = (this.research && this.research.branches) || [];
            return branches.flatMap((b) => b.researches.filter((r) => r.state === "IN_PROGRESS"));
        },
    };
}

import { useMemo } from 'react'
import { useColony } from '../contexts/ColonyContext'
import { useUi } from '../contexts/UiContext'
import type { ResearchBranch, ResearchEntry } from '../types/research'

/**
 * Research tab: branch selection and state filtering over the cached research tree.
 */
export function useResearch() {
  const { research } = useColony()
  const { researchBranch, setResearchBranch, researchFilter, setResearchFilter } = useUi()

  const researchBranches = useMemo(() => {
    const branches = research?.branches || []
    if (!researchBranch) return branches
    return branches.filter(b => b.id === researchBranch)
  }, [research, researchBranch])

  const researchIn = (branch: ResearchBranch): ResearchEntry[] => {
    if (researchFilter === 'all') return branch.researches
    return branch.researches.filter(entry => entry.state === researchFilter)
  }

  /** Anything the university is actively working on, across all branches. */
  const researchInProgress = useMemo(() => {
    const branches = research?.branches || []
    return branches.flatMap(b => b.researches.filter(r => r.state === 'IN_PROGRESS'))
  }, [research])

  return {
    research,
    researchBranch, setResearchBranch,
    researchFilter, setResearchFilter,
    researchBranches, researchIn, researchInProgress,
  }
}

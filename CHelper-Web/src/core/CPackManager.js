import { CHelperCore, createWasmFuture } from '@/core/libCHelperWeb.js'

import releaseVanillaCPack from '@/assets/release-vanilla-1.21.132.1.cpack?url'
import releaseExperimentCPack from '@/assets/release-experiment-1.21.132.1.cpack?url'
import betaVanillaCPack from '@/assets/beta-vanilla-1.26.0.29.cpack?url'
import betaExperimentCPack from '@/assets/beta-experiment-1.26.0.29.cpack?url'
import neteaseVanillaCPack from '@/assets/netease-vanilla-1.21.50.07.cpack?url'
import neteaseExperimentCPack from '@/assets/netease-experiment-1.21.50.07.cpack?url'

export const DEFAULT_BRANCH = 'release-experiment'

export const ALL_BRANCH = [
  'release-vanilla',
  'release-experiment',
  'beta-vanilla',
  'beta-experiment',
  'netease-vanilla',
  'netease-experiment',
]

export const ALL_BRANCH_CHINESE = [
  '正式版-原版-1.21.132.1',
  '正式版-实验性玩法-1.21.132.1',
  '测试版-原版-1.26.0.29',
  '测试版-实验性玩法-1.26.0.29',
  '中国版-原版-1.21.50.07',
  '中国版-实验性玩法-1.21.50.07',
]

let cpackCache = {}

export async function getCore(branch) {
  let cpack = cpackCache[branch]
  if (cpack === undefined) {
    cpack = await fetch(getRealFileName(branch))
      .then((response) => response.arrayBuffer())
      .then(async (cpack) => {
        return new Uint8Array(cpack)
      })
    cpackCache[branch] = cpack
  }
  await createWasmFuture
  return new CHelperCore(cpack)
}

export function getRealFileName(branch) {
  switch (branch) {
    case 'release-vanilla':
      return releaseVanillaCPack
    case 'release-experiment':
      return releaseExperimentCPack
    case 'beta-vanilla':
      return betaVanillaCPack
    case 'beta-experiment':
      return betaExperimentCPack
    case 'netease-vanilla':
      return neteaseVanillaCPack
    case 'netease-experiment':
      return neteaseExperimentCPack
    default:
      return getRealFileName(DEFAULT_BRANCH)
  }
}

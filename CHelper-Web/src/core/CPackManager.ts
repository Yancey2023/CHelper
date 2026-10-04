import { CHelperCore, createWasmFuture } from '@/core/libCHelperWeb'

import releaseVanillaCPack from '@/assets/release-vanilla-1.21.132.1.cpack?url'
import releaseExperimentCPack from '@/assets/release-experiment-1.21.132.1.cpack?url'
import betaVanillaCPack from '@/assets/beta-vanilla-1.26.0.29.cpack?url'
import betaExperimentCPack from '@/assets/beta-experiment-1.26.0.29.cpack?url'
import neteaseVanillaCPack from '@/assets/netease-vanilla-1.21.50.07.cpack?url'
import neteaseExperimentCPack from '@/assets/netease-experiment-1.21.50.07.cpack?url'

export type Branch =
  | 'release-vanilla'
  | 'release-experiment'
  | 'beta-vanilla'
  | 'beta-experiment'
  | 'netease-vanilla'
  | 'netease-experiment'

export const DEFAULT_BRANCH: Branch = 'release-experiment'

export const ALL_BRANCH: Branch[] = [
  'release-vanilla',
  'release-experiment',
  'beta-vanilla',
  'beta-experiment',
  'netease-vanilla',
  'netease-experiment',
]

export const ALL_BRANCH_CHINESE: string[] = [
  '正式版-原版-1.21.132.1',
  '正式版-实验性玩法-1.21.132.1',
  '测试版-原版-1.26.0.29',
  '测试版-实验性玩法-1.26.0.29',
  '中国版-原版-1.21.50.07',
  '中国版-实验性玩法-1.21.50.07',
]

const cpackCache: Partial<Record<Branch, Uint8Array>> = {}

export async function getCore(branch: Branch): Promise<CHelperCore> {
  let cpack = cpackCache[branch]
  if (cpack === undefined) {
    const response = await fetch(getRealFileName(branch))
    // 服务器返回 404/500 等错误页面时必须直接失败，
    // 否则错误页内容会被当成资源包解析，甚至被缓存导致重试永远失败
    if (!response.ok) {
      throw new Error(`fail to download cpack (${branch}): ${response.status} ${response.url}`)
    }
    const buffer = await response.arrayBuffer()
    cpack = new Uint8Array(buffer)
    cpackCache[branch] = cpack
  }
  await createWasmFuture
  return new CHelperCore(cpack)
}

export function getRealFileName(branch: Branch): string {
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

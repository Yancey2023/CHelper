import { CHelperCore, initializeWasm } from '@/core/libCHelperWeb'

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

const branchLabels: Record<Branch, string> = {
  'release-vanilla': '正式版-原版',
  'release-experiment': '正式版-实验性玩法',
  'beta-vanilla': '测试版-原版',
  'beta-experiment': '测试版-实验性玩法',
  'netease-vanilla': '中国版-原版',
  'netease-experiment': '中国版-实验性玩法',
}

const packFiles = Object.entries(
  import.meta.glob<string>('../../../build/artifacts/resources/cpack/*.cpack', {
    eager: true,
    query: '?url',
    import: 'default',
  }),
)
const bundledPacks = new Map(
  ALL_BRANCH.map((branch) => {
    const prefix = `${branch}-`
    const files = packFiles.filter(([path]) => path.split('/').pop()?.startsWith(prefix))
    if (files.length !== 1) throw new Error(`Expected one bundled resource pack for ${branch}`)
    const [path, url] = files[0]
    const version = path.slice(path.lastIndexOf('/') + 1 + prefix.length, -'.cpack'.length)
    return [branch, { url, version }] as const
  }),
)

export const ALL_BRANCH_CHINESE = ALL_BRANCH.map(
  (branch) => `${branchLabels[branch]}-${bundledPacks.get(branch)!.version}`,
)

const cpackCache: Partial<Record<Branch, Uint8Array>> = {}

export async function getCore(branch: Branch): Promise<CHelperCore> {
  const [cpack] = await Promise.all([loadCPack(branch), initializeWasm()])
  return new CHelperCore(cpack)
}

async function loadCPack(branch: Branch): Promise<Uint8Array> {
  const cached = cpackCache[branch]
  if (cached !== undefined) return cached

  const response = await fetch(getRealFileName(branch))
  if (!response.ok) {
    throw new Error(`fail to download cpack (${branch}): ${response.status} ${response.url}`)
  }
  const cpack = new Uint8Array(await response.arrayBuffer())
  cpackCache[branch] = cpack
  return cpack
}

export function getRealFileName(branch: Branch): string {
  return (bundledPacks.get(branch) ?? bundledPacks.get(DEFAULT_BRANCH)!).url
}

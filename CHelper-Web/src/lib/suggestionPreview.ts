const wikiMediaUrl = /^https:\/\/zh\.minecraft\.wiki\/images\/[A-Za-z0-9_%.()'!+,~-]+$/

export function isWikiSuggestionIconUrl(url: string): boolean {
  return wikiMediaUrl.test(url) && /\.(png|gif|webp)$/.test(url)
}

export function isWikiSuggestionMediaUrl(
  url: string,
  extension: '.png' | '.ogg' | '.wav',
): boolean {
  return url.endsWith(extension) && wikiMediaUrl.test(url)
}

const dyeColors: ReadonlyMap<string, string> = new Map([
  ['white_dye', '#F9FFFE'],
  ['orange_dye', '#F9801D'],
  ['magenta_dye', '#C74EBD'],
  ['light_blue_dye', '#3AB3DA'],
  ['yellow_dye', '#FED83D'],
  ['lime_dye', '#80C71F'],
  ['pink_dye', '#F38BAA'],
  ['gray_dye', '#474F52'],
  ['light_gray_dye', '#9D9D97'],
  ['cyan_dye', '#169C9C'],
  ['purple_dye', '#8932B8'],
  ['blue_dye', '#3C44AA'],
  ['brown_dye', '#835432'],
  ['green_dye', '#5E7C16'],
  ['red_dye', '#B02E26'],
  ['black_dye', '#1D1D21'],
])

export function dyeColor(id: string): string | undefined {
  return dyeColors.get(id)
}

function bounceOut(t: number): number {
  if (t < 1 / 2.75) return 7.5625 * t * t
  if (t < 2 / 2.75) return 7.5625 * (t - 1.5 / 2.75) ** 2 + 0.75
  if (t < 2.5 / 2.75) return 7.5625 * (t - 2.25 / 2.75) ** 2 + 0.9375
  return 7.5625 * (t - 2.625 / 2.75) ** 2 + 0.984375
}

// 示意 spring 的阻尼振荡，公开命令文档没有给出游戏内部的弹簧参数。
function spring(t: number): number {
  return (1 - Math.exp(-6 * t) * Math.cos(14 * t)) / (1 - Math.exp(-6) * Math.cos(14))
}

const easeIn = new Map<string, (t: number, symmetric: boolean) => number>([
  ['quad', (t) => t * t],
  ['cubic', (t) => t ** 3],
  ['quart', (t) => t ** 4],
  ['quint', (t) => t ** 5],
  ['sine', (t) => 1 - Math.cos((t * Math.PI) / 2)],
  ['expo', (t) => 2 ** (10 * (t - 1))],
  ['circ', (t) => 1 - Math.sqrt(1 - t * t)],
  ['bounce', (t) => 1 - bounceOut(1 - t)],
  [
    'back',
    (t, symmetric) => {
      const overshoot = symmetric ? 1.70158 * 1.525 : 1.70158
      return t * t * ((overshoot + 1) * t - overshoot)
    },
  ],
  [
    'elastic',
    (t, symmetric) => {
      const period = symmetric ? 0.45 : 0.3
      return -(2 ** (10 * (t - 1))) * Math.sin(((t - 1 - period / 4) * 2 * Math.PI) / period)
    },
  ],
])

export function easingGraph(id: string): string | undefined {
  const direction = ['in_out_', 'out_', 'in_'].find((prefix) => id.startsWith(prefix))
  const curve = direction ? easeIn.get(id.slice(direction.length)) : undefined
  if (!curve && id !== 'linear' && id !== 'spring') return undefined
  const points = Array.from({ length: 65 }, (_, index) => {
    const t = index / 64
    let value = t
    if (index !== 0 && index !== 64) {
      if (id === 'spring') value = spring(t)
      else if (curve && direction === 'out_') value = 1 - curve(1 - t, false)
      else if (curve && direction === 'in_out_') {
        value = t < 0.5 ? curve(2 * t, true) / 2 : 1 - curve(2 * (1 - t), true) / 2
      } else if (curve) value = curve(t, false)
    }
    return `${(t * 64).toFixed(2)},${(((1.4 - value) * 32) / 1.8).toFixed(2)}`
  })
  return points.join(' ')
}

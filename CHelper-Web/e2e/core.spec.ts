import { expect, test } from '@playwright/test'
import type { Suggestion } from '../src/core/libCHelperWeb'

test('retries failed Wasm initialization without reloading the page', async ({ page }) => {
  const wasm = (url: URL) => url.pathname.endsWith('/libCHelperWeb.wasm')
  await page.route(wasm, (route) =>
    route.request().resourceType() === 'fetch' ? route.abort() : route.continue(),
  )
  await page.goto('/')
  await expect(page.getByText('CHelper 内核加载失败')).toBeVisible()
  const navigationStart = await page.evaluate(() => performance.timeOrigin)

  await page.unroute(wasm)
  const retry = page.waitForResponse(
    (response) => response.request().resourceType() === 'fetch' && wasm(new URL(response.url())),
  )
  await page.getByRole('button', { name: '重试' }).click()
  expect((await retry).ok()).toBe(true)
  await expect(page.getByText('欢迎使用CHelper')).toBeVisible()
  await expect(page.getByRole('button', { name: '重试' })).toBeHidden()
  expect(await page.evaluate(() => performance.timeOrigin)).toBe(navigationStart)
})

test('loads every bundled resource pack with the generated Wasm core', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  await page.goto('/')

  const results = await page.evaluate(async () => {
    const modulePath = '/src/core/CPackManager.ts'
    const { ALL_BRANCH, getCore } = await import(/* @vite-ignore */ modulePath)
    const results = []
    for (const branch of ALL_BRANCH) {
      const core = await getCore(branch)
      try {
        const context = core.createContext('say hello')
        try {
          results.push({ branch, command: context.getCommand(), errors: context.getErrorReasons() })
        } finally {
          context.release()
        }
      } finally {
        core.release()
      }
    }
    return results
  })

  expect(results).toHaveLength(6)
  for (const result of results) {
    expect(result.command, result.branch).toBe('say hello')
    expect(result.errors, result.branch).toEqual([])
  }
  expect(errors).toEqual([])
})

test('exposes typed previews through both Wasm suggestion readers', async ({ page }) => {
  await page.goto('/')
  const previews = await page.evaluate(async () => {
    const modulePath = '/src/core/CPackManager.ts'
    const { getCore } = await import(/* @vite-ignore */ modulePath)
    const core = await getCore('release-vanilla')
    try {
      return [
        ['give @s diamond', 'diamond'],
        ['setblock ~ ~ ~ minecraft:dirt', 'minecraft:dirt'],
        ['playsound random.levelup', 'random.levelup'],
        ['playsound dig.stone', 'dig.stone'],
        ['camera @s set minecraft:free ease 1 in_quad', 'in_quad'],
        ['give @s blue_dye', 'blue_dye'],
        ['effect @s speed', 'speed'],
        ['give @s command_block', 'command_block'],
        ['setblock ~ ~ ~ minecraft:chain_command_block', 'minecraft:chain_command_block'],
        ['playsound mob.imitate.blaze', 'mob.imitate.blaze'],
      ].map(([command, id]) => {
        const context = core.createContext(command)
        try {
          const item = context
            .getAllSuggestions(command.length)
            .find((entry: Suggestion) => entry.title === id)
          const single = item && context.getSuggestion(command.length, item.id)
          return [item?.preview, single?.preview]
        } finally {
          context.release()
        }
      })
    } finally {
      core.release()
    }
  })
  expect(previews.map(([entry]) => entry?.kind)).toEqual([
    'icon',
    'icon',
    'audio',
    'audio',
    'easing',
    'dyeColor',
    'icon',
    'icon',
    'icon',
    'audio',
  ])
  for (const [entry, single] of previews) {
    expect(single).toEqual(entry)
    if (entry.kind === 'icon')
      expect(entry.value).toMatch(/^https:\/\/zh\.minecraft\.wiki\/images\/[^/]+\.(png|gif|webp)$/)
    if (entry.kind === 'audio')
      expect(entry.value).toMatch(/^https:\/\/zh\.minecraft\.wiki\/images\/[^/]+\.(ogg|wav)$/)
  }
  expect(previews[4][0].value).toBe('in_quad')
  expect(previews[5][0].value).toBe('blue_dye')
  expect(previews[7][0].value).toMatch(/\/Impulse_Command_Block\.gif$/)
  expect(previews[8][0].value).toMatch(/\/Chain_Command_Block\.gif$/)
})

test('uses verified Wiki files for missing previews in each edition', async ({ page }) => {
  await page.goto('/')
  const previews = await page.evaluate(async () => {
    const modulePath = '/src/core/CPackManager.ts'
    const { getCore } = await import(/* @vite-ignore */ modulePath)
    const cases = [
      ['release-vanilla', 'playsound note.bassattack', 'note.bassattack'],
      ['release-vanilla', 'playsound block.shelf.break', 'block.shelf.break'],
      ['beta-vanilla', 'give @s poplar_hanging_sign', 'poplar_hanging_sign'],
      ['beta-vanilla', 'playsound block.comparator.click', 'block.comparator.click'],
      ['beta-vanilla', 'playsound place.hanging_sign', 'place.hanging_sign'],
      ['beta-experiment', 'playsound mob.baby_nautilus.swim', 'mob.baby_nautilus.swim'],
      ['netease-vanilla', 'give @s chain', 'chain'],
      [
        'netease-experiment',
        'playsound trial_spawner.charge_activate',
        'trial_spawner.charge_activate',
      ],
    ]
    const previews = []
    for (const [branch, command, id] of cases) {
      const core = await getCore(branch)
      try {
        const context = core.createContext(command)
        try {
          const suggestion = context
            .getAllSuggestions(command.length)
            .find((entry: Suggestion) => entry.title === id)
          previews.push(suggestion?.preview)
        } finally {
          context.release()
        }
      } finally {
        core.release()
      }
    }
    return previews
  })
  expect(previews).toEqual([
    { kind: 'audio', value: expect.stringMatching(/\/Note\.bassattack\.wav$/) },
    { kind: 'audio', value: expect.stringMatching(/\/Chiseled_Bookshelf_break1\.ogg$/) },
    { kind: 'icon', value: expect.stringMatching(/\/BlockSprite_poplar-hanging-sign\.png$/) },
    { kind: 'audio', value: expect.stringMatching(/\/[^/]+\.(ogg|wav)$/) },
    { kind: 'audio', value: expect.stringMatching(/\/Hanging_Sign_break1\.ogg$/) },
    { kind: 'audio', value: expect.stringMatching(/\/Mob\.nautilus\.swim2\.wav$/) },
    { kind: 'icon', value: expect.stringMatching(/\/ItemSprite_iron-chain\.png$/) },
    { kind: 'audio', value: expect.stringMatching(/\/Trial_Spawner_ominous_activate\.ogg$/) },
  ])
})

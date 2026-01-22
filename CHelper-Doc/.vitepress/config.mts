import { defineConfig } from "vitepress";

// https://vitepress.dev/reference/site-config
export default defineConfig({
  title: "CHelper文档",
  description:
    "CHelper是一个我的世界基岩版的命令助手，致力于为我的世界指令玩家提供帮助。",
  lang: "zh-CN",
  cleanUrls: true,
  srcDir: "./src",

  head: [
    [
      "link",
      {
        rel: "icon",
        href: "https://www.yanceymc.cn/chelper/logo.webp",
      },
    ],
  ],

  locales: {
    root: {
      label: "简体中文",
      lang: "zh",
    },
  },

  themeConfig: {
    // https://vitepress.dev/reference/default-theme-config
    logo: "/logo.webp",

    editLink: {
      pattern: "https://github.com/Yancey2023/CHelper/edit/main/CHelper-Doc/src/:path",
      text: "在 GitHub 上编辑此页面",
    },

    docFooter: {
      prev: "上一页",
      next: "下一页",
    },

    outline: {
      label: "页面导航",
    },

    lastUpdated: {
      text: "最后更新于",
    },

    notFound: {
      title: "页面未找到",
      quote:
        "但如果你不改变方向，并且继续寻找，你可能最终会到达你所前往的地方。",
      linkLabel: "前往首页",
      linkText: "带我回首页",
    },

    langMenuLabel: "多语言",
    returnToTopLabel: "回到顶部",
    sidebarMenuLabel: "菜单",
    darkModeSwitchLabel: "主题",
    lightModeSwitchTitle: "切换到浅色模式",
    darkModeSwitchTitle: "切换到深色模式",
    skipToContentLabel: "跳转到内容",

    nav: [
      {
        text: "首页",
        link: "/",
      },
      {
        text: "文档",
        link: "/chelper",
      },
    ],

    sidebar: [
      {
        text: "简介",
        items: [
          {
            text: "什么是CHelper",
            link: "/chelper",
          },
          {
            text: "CHelper 更新日志",
            link: "/chelper-release-notes",
          },
          {
            text: "赞助",
            link: "/donate",
          },
        ],
      },
      {
        text: "资源包",
        items: [
          {
            text: "什么是CPack",
            link: "/cpack/cpack",
          },
          {
            text: "manifest.json",
            link: "/cpack/manifest",
          },
          {
            text: "命令的注册",
            link: "/cpack/command",
          },
          {
            text: "ID 的注册",
            link: "/cpack/id",
          },
          {
            text: "节点的定义",
            link: "/cpack/node",
          },
        ],
      },
      {
        text: "二次开发",
        items: [
          {
            text: "CHelper 内核文档",
            link: "/development/core",
          },
          {
            text: "CHelper 安卓接口 / Java 接口文档",
            link: "/development/android",
          },
          {
            text: "CHelper 网页接口 / JavaScript 接口文档",
            link: "/development/web",
          },
        ],
      },
    ],

    socialLinks: [
      {
        icon: "bilibili",
        link: "https://space.bilibili.com/470179011",
      },
      {
        icon: "qq",
        link: "https://qun.qq.com/universal-share/share?ac=1&authKey=TNhzRmfsG%2B2pxmSIE5UpHpYhPF1n67hSGBhzsfC6mc5xwQ6lg0xcfuJ3Z7iOBUOD&busi_data=eyJncm91cENvZGUiOiI3NjY2MjU1OTciLCJ0b2tlbiI6InBIdE1jc1NndWxseDBicklIQms2SExTSjFjSFkwcWpSUFMyR0YySjB1NERhNGc4WVA0YUxlVlFXakRnRjVZQXgiLCJ1aW4iOiIxNzA5MTg1NDgyIn0%3D&data=Ua2iHVoaLyx3R03s4Ff1e2waRR1Aqya0zyD_8GlSCN8MT-PWCdvWKGNsi1G4gjo-P7ssvWN0xCq4GEp4F_fbog&svctype=4&tempid=h5_group_info",
      },
      {
        icon: "github",
        link: "https://github.com/Yancey2023/CHelper-Core",
      },
    ],

    footer: {
      copyright: `©${new Date().getFullYear()} By Yancey`,
      message:
        '<a href="https://beian.miit.gov.cn/shouye.html">粤ICP备2024307783号</a>',
    },
  },

  lastUpdated: true,
});

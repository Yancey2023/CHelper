# CHelper Floating Window

这部分是 CHelper 悬浮窗相关代码，因为悬浮窗使用 compose 框架时输入框会有焦点问题，我不会修复，所以依旧使用着
View 组件。希望以后这个 bug 可以解决，让悬浮窗与 app 界面可以公用一套代码。

已解决：

1. 我们使用 FrameLayout 嵌套 ComposeView 获取返回键事件。因为 ComposeView 有 final 属性不能被继承，返回键事件继承
   dispatchKeyEvent 函数进行获取比较靠谱，所以只能使用 FrameLayout 进行嵌套获取返回键事件。
2. ComposeView 的 context 传入 Application 会导致内嵌 View 作为输入框无法获取焦点，需要改为传入 Activity 作为
   context。

待解决：

1. 输入框获取到焦点后，无法响应键盘输入。FrameLayout 的 dispatchKeyEvent 函数可以获取到按键事件，
   需要进一步排查是在哪一步被拦截了。因为 compose 的代码库太庞大，我暂时还无从下手。
2. 输入框获取到焦点后切换页面，获取到焦点的输入框会消失，会导致后续无法获取返回键事件。所以需要在切换页面的时候让
   FrameLayout 获取焦点，以取消输入框的焦点。目前只是在监听到返回键的时候处理了焦点问题，但是这样做是有问题的。
   因为除了返回上一个页面需要要请求焦点，打开新页面也需要请求焦点，所以我们需要监听 compose 的导航事件来处理这个问题，
   但是目前我没找到相关 API。

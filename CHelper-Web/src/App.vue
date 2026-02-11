<script>
import { ALL_BRANCH, ALL_BRANCH_CHINESE, DEFAULT_BRANCH, getCore } from '@/core/CPackManager.js'
import SelectorModal from '@/components/SelectorModal.vue'
import Editor from '@/components/Editor.vue'
import IcpFooter from '@/components/IcpFooter.vue'

export default {
  components: {
    SelectorModal,
    Editor,
    IcpFooter,
  },
  data() {
    return {
      ALL_BRANCH: ALL_BRANCH,
      ALL_BRANCH_CHINESE: ALL_BRANCH_CHINESE,
      structure: 'CHelper正在加载中，请稍候',
      paramHint: '作者：Yancey',
      errorReason: '',
      suggestions: [],
      realSuggestionSize: 0,
      isBranchSelectorVisible: false,
      editorValue: {
        text: '',
        cursorPosition: 0,
      },
      syntaxTokens: [],
    }
  },
  async created() {
    this.core = undefined
    this.setCore(await getCore(DEFAULT_BRANCH))
  },
  mounted() {
    this.resizeObserver = new ResizeObserver(() => {
      this.onSuggestionScroll()
    })
    this.resizeObserver.observe(this.$refs.listRef)
  },
  unmounted() {
    this.resizeObserver.disconnect()
    this.release()
  },
  methods: {
    setCore(newCore) {
      if (this.core !== undefined) {
        this.core.release()
      }
      this.core = newCore
      this.onEditorValueChanged(this.editorValue)
    },
    release() {
      if (this.core === undefined) {
        return
      }
      this.core.release()
      this.core = undefined
    },
    updateSuggestions() {
      this.realSuggestionSize = this.core.getSuggestionSize()
      this.suggestions = []
      this.loadMore(Math.floor(this.$refs.listRef.clientHeight / 25))
    },
    onEditorValueChanged(newEditorValue) {
      if (newEditorValue.text.length === 0) {
        this.editorValue = newEditorValue
        this.structure = '欢迎使用CHelper'
        this.paramHint = '作者：Yancey'
        this.errorReason = ''
        if (this.core !== undefined) {
          this.core.onTextChanged(this.editorValue.text, this.editorValue.cursorPosition)
          this.updateSuggestions()
        }
        return
      }
      if (this.core === undefined) {
        return
      }
      if (this.editorValue.text === newEditorValue.text) {
        if (this.editorValue.cursorPosition === newEditorValue.cursorPosition) {
          return
        }
        this.editorValue = newEditorValue
        this.core.onSelectionChanged(this.editorValue.cursorPosition)
      } else {
        this.editorValue = newEditorValue
        this.core.onTextChanged(this.editorValue.text, this.editorValue.cursorPosition)
        this.structure = this.core.getStructure()
        const errorReasons = this.core.getErrorReasons()
        if (errorReasons.length === 0) {
          this.errorReason = ''
        } else if (errorReasons.length === 1) {
          this.errorReason = errorReasons[0].errorReason
        } else {
          this.errorReason = '可能的错误原因：'
          for (let i = 0; i < errorReasons.length; i++) {
            this.errorReason += `\n${i + 1}. ${errorReasons[i].errorReason}`
          }
        }
        this.syntaxTokens = this.core.getSyntaxTokens()
      }
      this.paramHint = this.core.getParamHint()
      this.updateSuggestions()
    },
    loadMore(count) {
      if (this.core === undefined) {
        return
      }
      const start = this.suggestions.length
      const end = Math.min(start + count, this.realSuggestionSize)
      for (let i = start; i < end; i++) {
        this.suggestions.push(this.core.getSuggestion(i))
      }
    },
    onSuggestionScroll() {
      if (
        this.$refs.listRef.scrollTop + 2 * this.$refs.listRef.clientHeight >=
        this.$refs.listRef.scrollHeight
      ) {
        this.loadMore(Math.floor(this.$refs.listRef.clientHeight / 25))
      }
    },
    onSuggestionClick(which) {
      if (this.core === undefined) {
        return
      }
      const clickSuggestionResult = this.core.onSuggestionClick(which)
      if (clickSuggestionResult == null) {
        return
      }
      this.onEditorValueChanged({
        text: clickSuggestionResult.newText,
        cursorPosition: clickSuggestionResult.cursorPosition,
      })
    },
    selectBranch() {
      this.openBranchSelector()
    },
    copy() {
      navigator.clipboard.writeText(this.editorValue.text).catch(function (reason) {
        window.alert('复制失败：' + reason)
      })
    },
    openBranchSelector() {
      this.isBranchSelectorVisible = true
    },
    closeBranchSelector() {
      this.isBranchSelectorVisible = false
    },
    async onBranchSelect(branch) {
      this.setCore(await getCore(branch))
    },
  },
}
</script>

<template>
  <div class="container">
    <header class="header">
      <div>
        <div class="text-structure">{{ structure }}</div>
        <div class="text-param-hint">{{ paramHint }}</div>
        <div class="text-error-reason" v-if="errorReason">{{ errorReason }}</div>
        <div class="line"></div>
      </div>
    </header>
    <main ref="listRef" @scroll="onSuggestionScroll">
      <div class="div-suggestion" v-for="item in suggestions" @click="onSuggestionClick(item.id)">
        <div class="text-suggestion-name">{{ item.title }}</div>
        <div class="text-suggestion-description">{{ item.description }}</div>
      </div>
    </main>
    <footer>
      <div class="below">
        <button class="button" @click="selectBranch">分支</button>
        <Editor
          :modelValue="editorValue"
          :syntaxTokens="syntaxTokens"
          @update:modelValue="onEditorValueChanged"
        />
        <button class="button" @click="copy">复制</button>
      </div>
      <IcpFooter />
    </footer>
    <SelectorModal
      :title="'选择分支'"
      :data="this.ALL_BRANCH"
      :showNames="this.ALL_BRANCH_CHINESE"
      :show="isBranchSelectorVisible"
      @close="closeBranchSelector"
      @select="onBranchSelect"
    />
  </div>
</template>

<style scoped>
.container {
  display: grid;
  height: calc(var(--vh, 1vh) * 100);
  grid-template-rows: auto 1fr auto;
}

.line {
  margin: 5px 10px;
  height: 1px;
  background: darkgrey;
}

main {
  flex: 1;
  overflow-y: auto;
}

.text-structure {
  height: auto;
  padding: 10px;
  margin: 5px 5px 0 5px;
  color: #000000;
  background-color: #f5f7fa;
  border-radius: 5px;
  white-space: pre-wrap;
}

.text-param-hint {
  height: auto;
  padding: 10px;
  margin: 5px 5px 0 5px;
  color: #666666;
  background-color: #f5f7fa;
  border-radius: 5px;
  white-space: pre-wrap;
}

.text-error-reason {
  height: auto;
  padding: 10px;
  margin: 5px 5px 0 5px;
  color: #ff4444;
  background-color: #f5f7fa;
  border-radius: 5px;
  white-space: pre-wrap;
}

.div-suggestion {
  height: auto;
  padding: 5px;
  margin: 0 5px 5px 5px;
  background-color: #f5f7fa;
  border-radius: 5px;
  cursor: pointer;
}

.div-suggestion:hover {
  background-color: #f5f5f5;
}

.text-suggestion-name {
  height: auto;
  color: #000000;
  margin: 5px;
}

.text-suggestion-description {
  height: auto;
  color: #666666;
  margin: 5px;
}

.below {
  display: grid;
  left: 20px;
  width: calc(100vw - 10px);
  margin: 5px;
  grid-template-columns: auto 1fr auto;
  align-items: center;
}

.button {
  padding: 5px;
  border: 0;
  width: 50px;
  height: 100%;
  color: #f5f7fa;
  text-align: center;
  background: #007bff;
  border-radius: 5px;
  cursor: pointer;
}

.button:hover {
  background-color: #0070ff;
}

* {
  font-size: 15px;
  font-family: Inter, 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB',
    'Microsoft YaHei', '微软雅黑', Arial, sans-serif;
}
</style>

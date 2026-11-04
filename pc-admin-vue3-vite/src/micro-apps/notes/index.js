/**
 * 微前端演示子应用：notes 便签。
 *
 * 契约（主壳 MicroFrontendHost 约定）：
 *   export default { name, title, mount(container, ctx), unmount(ctx) }
 * 状态独立于主应用（localStorage 持久化，key 带应用名前缀隔离），
 * 演示「子应用生命周期与主壳解耦、卸载后状态不丢」。
 */
const STORAGE_KEY = 'micro-app-notes'
let root = null

function loadNotes() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    const parsed = raw ? JSON.parse(raw) : []
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function saveNotes(notes) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(notes))
  } catch {
    // 存储不可用（隐私模式等）时静默降级为会话内状态
  }
}

function render(container, ctx) {
  root = document.createElement('div')
  root.className = 'micro-notes'
  root.setAttribute('data-micro-app', 'notes')

  const notes = loadNotes()
  root.innerHTML = `
    <div style="padding:8px 4px;">
      <h3 style="margin:0 0 8px;">📝 Notes from micro-app「notes」</h3>
      <p style="margin:0 0 10px;color:#909399;font-size:13px;">
        便签持久化在 localStorage（key=${STORAGE_KEY}），卸载重挂后状态保留。
      </p>
      <div style="display:flex;gap:8px;margin-bottom:12px;">
        <input type="text" class="micro-notes-input" placeholder="写点什么…"
          style="flex:1;max-width:320px;padding:7px 10px;border:1px solid #dcdfe6;border-radius:6px;" />
        <button type="button" class="micro-notes-add"
          style="padding:7px 16px;border:none;border-radius:6px;background:#67c23a;color:#fff;cursor:pointer;">添加</button>
      </div>
      <ul class="micro-notes-list" style="list-style:none;margin:0;padding:0;max-width:420px;"></ul>
    </div>
  `

  const input = root.querySelector('.micro-notes-input')
  const addBtn = root.querySelector('.micro-notes-add')
  const list = root.querySelector('.micro-notes-list')

  function renderList() {
    const current = loadNotes()
    list.innerHTML = current.length === 0
      ? '<li style="color:#c0c4cc;font-size:13px;padding:6px 0;">暂无便签</li>'
      : ''
    current.forEach((note, index) => {
      const li = document.createElement('li')
      li.style.cssText = 'display:flex;justify-content:space-between;align-items:center;gap:10px;padding:8px 10px;border:1px solid #ebeef5;border-radius:6px;margin-bottom:6px;font-size:13px;'
      const span = document.createElement('span')
      span.textContent = note
      const del = document.createElement('button')
      del.type = 'button'
      del.textContent = '删除'
      del.style.cssText = 'border:none;background:none;color:#f56c6c;cursor:pointer;font-size:12px;'
      del.addEventListener('click', () => {
        const next = loadNotes().filter((_, i) => i !== index)
        saveNotes(next)
        renderList()
      })
      li.appendChild(span)
      li.appendChild(del)
      list.appendChild(li)
    })
  }

  addBtn.addEventListener('click', () => {
    const text = (input.value || '').trim()
    if (!text) return
    const next = loadNotes()
    next.push(text)
    saveNotes(next)
    input.value = ''
    renderList()
  })
  input.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') addBtn.click()
  })

  renderList()
  container.appendChild(root)
}

export default {
  name: 'notes',
  title: 'Notes 便签',
  mount(container, ctx) {
    render(container, ctx)
    return Promise.resolve()
  },
  unmount() {
    if (root) {
      root.remove()
      root = null
    }
    return Promise.resolve()
  }
}

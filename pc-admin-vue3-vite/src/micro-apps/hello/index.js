/**
 * 微前端演示子应用：hello 计数器。
 *
 * 契约（主壳 MicroFrontendHost 约定）：
 *   export default { name, title, mount(container, ctx), unmount(ctx) }
 * 技术栈零依赖（原生 DOM），验证「任意技术栈均可挂载」。
 */
let root = null
let count = 0

function render(container, ctx) {
  root = document.createElement('div')
  root.className = 'micro-hello'
  root.setAttribute('data-micro-app', 'hello')
  root.innerHTML = `
    <div style="display:flex;flex-direction:column;gap:12px;align-items:flex-start;padding:8px 4px;">
      <h3 style="margin:0;">👋 Hello from micro-app「hello」</h3>
      <p style="margin:0;color:#909399;font-size:13px;">
        由 MicroFrontendHost 通过 import.meta.glob 懒加载挂载，原生 DOM 实现，ctx.locale = ${ctx?.locale || '-'}
      </p>
      <button type="button" class="micro-hello-btn"
        style="padding:8px 18px;border:none;border-radius:6px;background:#409eff;color:#fff;font-size:14px;cursor:pointer;">
        已点击 0 次
      </button>
    </div>
  `
  const btn = root.querySelector('.micro-hello-btn')
  btn.addEventListener('click', () => {
    count += 1
    btn.textContent = `已点击 ${count} 次`
  })
  container.appendChild(root)
}

export default {
  name: 'hello',
  title: 'Hello 计数器',
  mount(container, ctx) {
    count = 0
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

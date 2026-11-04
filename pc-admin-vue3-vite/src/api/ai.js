import { request } from './http'

// R3 AI 辅助开发：AI 脚手架生成（后端 AiScaffoldController）。
// 统一经拆包层发出，复用 Result<T> 拆包与统一错误处理；注释不含 URL，以免干扰契约测试计数。

// 提交生成请求：prompt 为自然语言描述，moduleName 为可选目标模块名
export const generateScaffold = (body) =>
  request({ method: 'POST', url: '/api/ai/scaffold/generate', data: body })

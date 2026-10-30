import { request } from './http'

// GET /api/system/data-scope/resources -> List<DataScopeResourceView>
// DataScopeResourceView: { resourceCode, resourceName }
// 资源清单由后端 DataScopeResourceRegistry 启动时自动扫描 @DataScope 得到，
// 新增受控资源无需改前端常量即可出现在本页。
export const listDataScopeResources = () =>
  request({ method: 'GET', url: '/api/system/data-scope/resources' })

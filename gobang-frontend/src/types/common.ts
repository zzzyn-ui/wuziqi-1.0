// 通用响应结果接口
export interface Result<T = any> {
  code: number
  message: string
  data: T
}

// 分页请求参数
export interface PageParams {
  page: number
  pageSize: number
}

// 分页响应结果
export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

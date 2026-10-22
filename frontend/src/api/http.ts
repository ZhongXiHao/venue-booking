interface ApiResponse<T> {
  code: string
  message: string
  data: T
}

export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public data: unknown = null,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

export async function request<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json')

  if (typeof options.body === 'string') {
    headers.set('Content-Type', 'application/json')
  }

  let response: Response

  try {
    response = await fetch(`/api${path}`, {
      ...options,
      headers,
    })
  } catch {
    throw new ApiError(
      0,
      'NETWORK_ERROR',
      '无法连接服务，请检查网络和后端状态',
    )
  }

  let result: ApiResponse<T>

  try {
    result = await response.json()
  } catch {
    throw new ApiError(
      response.status,
      'INVALID_RESPONSE',
      '接口未返回有效JSON，请检查后端或代理配置',
    )
  }

  if (!response.ok || result.code !== 'SUCCESS') {
    throw new ApiError(
      response.status,
      result.code || 'UNKNOWN_ERROR',
      result.message || '请求失败',
      result.data,
    )
  }

  return result.data
}
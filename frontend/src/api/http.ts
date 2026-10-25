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

// 校验失败时，后端在 data.fieldErrors 里给出 [{field, message}]
export function getFieldErrors(error: unknown): Record<string, string> {
  if (!(error instanceof ApiError)) return {}

  const data = error.data as { fieldErrors?: unknown } | null
  if (!data || !Array.isArray(data.fieldErrors)) return {}

  const result: Record<string, string> = {}

  for (const item of data.fieldErrors as unknown[]) {
    if (typeof item !== 'object' || item === null) continue

    const { field, message } = item as Record<string, unknown>
    if (typeof field !== 'string' || typeof message !== 'string') continue

    result[field] = result[field] ? `${result[field]}；${message}` : message
  }

  return result
}

// 把接口错误拆成"表单里有对应输入框的字段错误"和一条总提示。
// 没有输入框的字段（如 venueId）并入总提示，避免错误被吞掉。
export function parseApiError(
  error: unknown,
  fallback: string,
  knownFields: string[],
) {
  const fieldErrors: Record<string, string> = {}
  const others: string[] = []

  for (const [field, message] of Object.entries(getFieldErrors(error))) {
    if (knownFields.includes(field)) {
      fieldErrors[field] = message
    } else {
      others.push(`${field}：${message}`)
    }
  }

  const base = error instanceof Error ? error.message : fallback
  const message = others.length ? `${base}（${others.join('；')}）` : base

  return { message, fieldErrors }
}

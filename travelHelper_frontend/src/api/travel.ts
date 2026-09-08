import request from '@/utils/request'

// 旅行推荐请求参数接口
export interface TravelRecommendParams {
  city: string
  budget: number
  days: number
}

// 旅行推荐响应接口
export interface TravelRecommendResponse {
  // 根据实际接口返回定义
  success?: boolean
  data?: any
  message?: string
}

/**
 * 旅行推荐接口
 * @param params 请求参数
 * @returns Promise<TravelRecommendResponse>
 */
export const travelRecommend = (params: TravelRecommendParams): Promise<TravelRecommendResponse> => {
  return request({
    url: '/api/travel/recommend',
    method: 'POST',
    data: params
  })
}
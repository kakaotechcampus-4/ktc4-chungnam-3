// 콜드스타트 큐. 앱이 뜨기 전에 발생한 이벤트를 보관했다가 넘긴다.

export async function enqueue(item: unknown): Promise<void> {}

export async function drain(): Promise<unknown[]> {
  return [];
}

// 로컬 영속 저장소 접근 래퍼.

export async function read<T>(key: string): Promise<T | null> {
  return null;
}

export async function write<T>(key: string, value: T): Promise<void> {}

export async function remove(key: string): Promise<void> {}

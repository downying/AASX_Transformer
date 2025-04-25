import Link from 'next/link'
import React from 'react'

function Header() {
  return (
    <header className="w-full bg-[#1A5B95] py-2 px-6 flex flex-col">
      {/* 1행: 로고 중앙 */}
      <div className="flex justify-center py-2">
        <Link href="/main" className="text-3xl font-bold text-white">
          AASX Transformer
        </Link>
      </div>

      {/* 2행: 버튼 우측 */}
      <div className="flex justify-end mt-2">
        <Link href="/admin" className="text-white font-bold">
          Management
        </Link>
      </div>
    </header>
  )
}

export default Header

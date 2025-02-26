import Link from 'next/link'
import React from 'react'

function Header() {
  return (
    <header className="w-full bg-[#1A5B95] py-4">
        <h1 className="text-center text-3xl font-bold text-white">
          {/* 로그인 기능 전 상태라서 헤더 클릭 시 메인 화면으로 이동하도록 설정
              - 로그인 기능 구현 시, 
              - 로그인 ⭕ -> href="/main"
              - 로그인 ❌ -> href="/" */}
          <Link href="/main" passHref>AASX Transformer</Link>
        </h1>
    </header>
  )
}

export default Header

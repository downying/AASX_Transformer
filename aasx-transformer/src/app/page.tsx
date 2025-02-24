import Footer from "@/components/common/Footer";
import Header from "@/components/common/Header";
import MainPage from "@/components/common/MainPage";
import Start from "@/components/common/Start";

export default function Home() {
  return (
    <div className="flex flex-col items-center justify-center h-screen bg-white">
      {/* 헤더 */}
      <Header />

      {/* 메인 */}
      <Start />

      {/* 푸터 */}
      <Footer />
      
    </div>
  );
}

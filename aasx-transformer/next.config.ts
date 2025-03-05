/** @type {import('next').NextConfig} */
const nextConfig = {
  async rewrites() {
    return [
      {
        source: "/api/transformer/:path*",
        destination: "http://localhost:8080/api/transformer/:path*",
      },
    ];
  },
  experimental: {
    serverActions: {
        bodySizeLimit: '50mb',
    }
}
};

export default nextConfig;

/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: false,
  async rewrites() {
    return [
      {
        source: "/api/transformer/:path*",
        destination: "http://localhost:8443/api/transformer/:path*",
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

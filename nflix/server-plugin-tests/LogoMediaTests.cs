using System.Text;
using Jellyfin.Plugin.NflixAuth;
using Xunit;

public class LogoMediaTests
{
    private static byte[] B(params int[] v) => v.Select(x => (byte)x).Concat(new byte[16]).ToArray();

    [Fact]
    public void RecognisesPictures()
    {
        Assert.Equal(("image", "image/png"), LogoMedia.Detect(B(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)));
        Assert.Equal(("image", "image/jpeg"), LogoMedia.Detect(B(0xFF, 0xD8, 0xFF, 0xE0)));
        Assert.Equal(("animated", "image/gif"), LogoMedia.Detect(Encoding.ASCII.GetBytes("GIF89a......")));
    }

    [Fact]
    public void TellsStillFromAnimatedWebp()
    {
        var still = Encoding.ASCII.GetBytes("RIFF\0\0\0\0WEBPVP8 \0\0\0\0\0\0\0\0");
        Assert.Equal(("image", "image/webp"), LogoMedia.Detect(still));
        var animated = Encoding.ASCII.GetBytes("RIFF\0\0\0\0WEBPVP8X\0\0\0\0").Concat(new byte[] { 0x02, 0, 0, 0 }).ToArray();
        Assert.Equal(("animated", "image/webp"), LogoMedia.Detect(animated));
    }

    [Fact]
    public void RecognisesVideos()
    {
        Assert.Equal(("video", "video/mp4"), LogoMedia.Detect(Encoding.ASCII.GetBytes("\0\0\0\u0018ftypmp42....")));
        Assert.Equal(("video", "video/webm"), LogoMedia.Detect(B(0x1A, 0x45, 0xDF, 0xA3)));
    }

    [Fact]
    public void RecognisesLottieOnly()
    {
        Assert.Equal(("lottie", "application/json"), LogoMedia.Detect(Encoding.UTF8.GetBytes(" {\"v\":\"5.7.4\",\"fr\":30,\"ip\":0,\"op\":60,\"w\":200,\"h\":80,\"layers\":[]}")));
        Assert.Null(LogoMedia.Detect(Encoding.UTF8.GetBytes("{\"hello\":1}")));
        Assert.Null(LogoMedia.Detect(Encoding.UTF8.GetBytes("{not json")));
    }

    [Fact]
    public void RefusesMarkup()
    {
        Assert.Null(LogoMedia.Detect(Encoding.UTF8.GetBytes("<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>")));
        Assert.Null(LogoMedia.Detect(Encoding.UTF8.GetBytes("<html><script>alert(1)</script></html>")));
        Assert.Null(LogoMedia.Detect(Array.Empty<byte>()));
    }

    [Fact]
    public void LimitsDependOnKind()
    {
        Assert.Equal(8 * 1024 * 1024, LogoMedia.MaxBytes("video"));
        Assert.Equal(2 * 1024 * 1024, LogoMedia.MaxBytes("image"));
    }
}

using Jellyfin.Plugin.NflixAuth;
using Xunit;

public class AvatarImageTests
{
    [Fact]
    public void ReadsInlinePictures()
    {
        var jpeg = new byte[] { 0xFF, 0xD8, 0xFF, 0xE0, 1, 2, 3 };
        Assert.Equal(jpeg, AvatarImage.FromDataUri("data:image/jpeg;base64," + Convert.ToBase64String(jpeg)));
        Assert.Null(AvatarImage.FromDataUri("data:image/svg+xml;base64,PHN2Zz4="));
        Assert.Null(AvatarImage.FromDataUri("data:image/png;base64," + Convert.ToBase64String(new byte[] { 1, 2, 3, 4 })));
        Assert.Null(AvatarImage.FromDataUri("data:image/png;base64,***"));
    }

    [Fact]
    public void KeepsUploadedFilesOnly()
    {
        Assert.Equal("6081b0d0-d4c6-45c0-90dd-51bdb592d760.jpeg", AvatarImage.UploadedFileName("https://auth.miracloud.fr/media/user-avatars/6081b0d0-d4c6-45c0-90dd-51bdb592d760.jpeg"));
        Assert.Null(AvatarImage.UploadedFileName("https://auth.miracloud.fr/media/user-avatars/default_avatar.png"));
        Assert.Null(AvatarImage.UploadedFileName("https://auth.miracloud.fr/media/user-avatars/../../etc/passwd"));
        Assert.Null(AvatarImage.UploadedFileName("file:///media/user-avatars/a.jpg"));
    }

    [Fact]
    public void ChecksIdsAndFormats()
    {
        Assert.True(AvatarImage.IsUserId("c2e60b7d95bf4153aebc57013594d44e"));
        Assert.False(AvatarImage.IsUserId("../c2e60b7d95bf4153aebc57013594d44e"));
        Assert.Equal("gif", AvatarImage.Sniff("GIF89a"u8));
        Assert.Null(AvatarImage.Sniff("<svg"u8));
    }
}

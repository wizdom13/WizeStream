import {
  Box, Button, Card, CardContent, Divider, List, ListItem, ListItemIcon, ListItemText,
  Stack, Typography,
} from '@mui/material';
import InfoRounded from '@mui/icons-material/InfoRounded';
import OpenInNewRounded from '@mui/icons-material/OpenInNewRounded';
import wizestreamLogo from '../../../assets/wizestream_logo_round.svg';

const aboutLinks = {
  faq: 'https://github.com/wizdom13/WizeStream/blob/pipe/desktop/docs/faq.md',
  github: 'https://github.com/wizdom13/WizeStream',
  donation: 'https://newpipe.net/donate/',
  website: 'https://github.com/wizdom13/WizeStream',
  privacy: 'https://github.com/wizdom13/WizeStream/blob/pipe/PRIVACY.md',
  license: 'https://github.com/wizdom13/WizeStream/blob/pipe/LICENSE',
} as const;

function openLink(url: string) {
  window.open(url, '_blank', 'noopener');
}

export function AboutPanel({ currentVersion }: { currentVersion?: string }) {
  return <Stack spacing={3}>
    <Box>
      <Typography variant="h4">About & FAQ</Typography>
      <Typography color="text.secondary">Project information, help, privacy, and licenses.</Typography>
    </Box>

    <Card variant="outlined"><CardContent sx={{ p: { xs: 2.5, md: 4 } }}>
      <Stack sx={{ alignItems: 'center', textAlign: 'center', mb: 3 }}>
        <Box component="img" src={wizestreamLogo} alt="WizeStream" sx={{ width: 112, height: 112, mb: 1.5 }} />
        <Typography variant="h4">WizeStream Desktop</Typography>
        <Typography color="text.secondary">Version {currentVersion ?? 'unknown'}</Typography>
        <Typography color="text.secondary" sx={{ mt: 1, maxWidth: 680 }}>
          Libre, privacy-friendly streaming for Windows, macOS, and Linux.
        </Typography>
      </Stack>

      <Card variant="outlined" sx={{ mb: 3, bgcolor: 'action.hover' }}>
        <CardContent>
          <Typography variant="h6">WizeStream</Typography>
          <Typography color="text.secondary" sx={{ mt: 0.75 }}>
            WizeStream is an independently maintained, multi-platform streaming application based on NewPipe.
            It is not affiliated with, sponsored by, or endorsed by the official NewPipe project, TeamNewPipe,
            or NewPipe e.V.
          </Typography>
          <Typography sx={{ mt: 1.25 }}>Author and developer: Wissam Shehadeh (Wisso)</Typography>
          <Typography variant="h6" sx={{ mt: 2.5 }}>Upstream NewPipe attribution</Typography>
          <Typography color="text.secondary" sx={{ mt: 0.75 }}>
            WizeStream is built from NewPipe and preserves the NewPipe libre software license, upstream credits,
            and third-party license notices.
          </Typography>
        </CardContent>
      </Card>

      <List disablePadding>
        <AboutAction icon={<InfoRounded />} title="Frequently asked questions"
          summary="If you are having trouble using the app, check these answers to common Desktop questions."
          action="View on website" onClick={() => openLink(aboutLinks.faq)} />
        <Divider component="li" />
        <AboutAction icon={<OpenInNewRounded />} title="Contribute to WizeStream"
          summary="Report issues, review code, or contribute to WizeStream on GitHub."
          action="View on GitHub" onClick={() => openLink(aboutLinks.github)} />
        <Divider component="li" />
        <AboutAction icon={<OpenInNewRounded />} title="Support upstream NewPipe"
          summary="NewPipe is developed by volunteers. You can support the upstream project through its donation page."
          action="Give back" onClick={() => openLink(aboutLinks.donation)} />
        <Divider component="li" />
        <AboutAction icon={<OpenInNewRounded />} title="Website"
          summary="Visit the WizeStream project page for more information and news."
          action="Open in browser" onClick={() => openLink(aboutLinks.website)} />
        <Divider component="li" />
        <AboutAction icon={<OpenInNewRounded />} title="WizeStream's Privacy Policy"
          summary="Learn what data WizeStream stores locally and when it connects to external services."
          action="Read privacy policy" onClick={() => openLink(aboutLinks.privacy)} />
        <Divider component="li" />
        <AboutAction icon={<OpenInNewRounded />} title="WizeStream's License"
          summary="WizeStream is copyleft libre software released under the GNU General Public License."
          action="Read license" onClick={() => openLink(aboutLinks.license)} />
      </List>
    </CardContent></Card>
  </Stack>;
}

function AboutAction({ icon, title, summary, action, onClick }: {
  icon: React.ReactNode;
  title: string;
  summary: string;
  action: string;
  onClick(): void;
}) {
  return <ListItem sx={{ py: 2.5 }}>
    <ListItemIcon>{icon}</ListItemIcon>
    <ListItemText primary={title} secondary={summary} />
    <Button sx={{ ml: 2 }} variant="outlined" onClick={onClick}>{action}</Button>
  </ListItem>;
}

(async () => {
  return JSON.stringify({
    href: location.href,
    title: document.title,
    readyState: document.readyState,
    正文前200字: document.body ? document.body.innerText.slice(0, 200) : "(无 body)",
    有turnstile: !!document.querySelector("iframe[src*=challenges], #challenge-form, .cf-turnstile")
  }, null, 1);
})()
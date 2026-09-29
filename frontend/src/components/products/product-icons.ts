import {
  AirVent,
  Camera,
  Car,
  CookingPot,
  Headphones,
  Laptop,
  Monitor,
  Package,
  Printer,
  Refrigerator,
  Smartphone,
  Sofa,
  Speaker,
  Tv,
  WashingMachine,
  Watch,
  type LucideIcon,
} from 'lucide-react';

const CATEGORY_ICONS: Record<string, LucideIcon> = {
  'mobile-phones': Smartphone,
  computers: Laptop,
  'tv-entertainment': Tv,
  audio: Headphones,
  'home-appliances': WashingMachine,
  kitchen: CookingPot,
  furniture: Sofa,
  vehicles: Car,
  other: Package,
};

/** Product-name keywords win over the category so a printer never shows a laptop. */
const KEYWORD_ICONS: [RegExp, LucideIcon][] = [
  [/printer/i, Printer],
  [/laptop|notebook|macbook|inspiron|thinkpad|vivobook/i, Laptop],
  [/monitor|display/i, Monitor],
  [/i?phone|galaxy s|pixel|mobile|smartphone/i, Smartphone],
  [/\btv\b|television|bravia|oled|qled/i, Tv],
  [/soundbar|speaker/i, Speaker],
  [/head ?phones?|earbuds?|earphones?|airpods|headset|rockerz|wh-1000/i, Headphones],
  [/refrigerator|fridge/i, Refrigerator],
  [/washing|washer|dryer/i, WashingMachine],
  [/\bac\b|air ?condition|inverter split|split ac/i, AirVent],
  [/air ?fryer|microwave|oven|mixer|grinder|kettle|cooker/i, CookingPot],
  [/watch/i, Watch],
  [/camera|dslr|gopro/i, Camera],
  [/sofa|couch|chair|table|bed|wardrobe/i, Sofa],
  [/\bcar\b|bike|scooter|vehicle/i, Car],
];

export function iconForProduct(name: string | null | undefined, categorySlug: string | null | undefined): LucideIcon {
  if (name) {
    const match = KEYWORD_ICONS.find(([pattern]) => pattern.test(name));
    if (match) return match[1];
  }
  return (categorySlug && CATEGORY_ICONS[categorySlug]) || Package;
}

export function iconForCategory(categorySlug: string | null | undefined): LucideIcon {
  return (categorySlug && CATEGORY_ICONS[categorySlug]) || Package;
}
